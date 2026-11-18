package com.cardemo.payment;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.LoggerFactory;

public interface BillPaymentPaymentService
{
	enum Outcome
	{
		PREVIEW,
		CANCELLED,
		PAID,
		INVALID_REQUEST,
		ACCOUNT_NOT_FOUND,
		PAYMENT_REJECTED,
		UNAVAILABLE
	}

	enum Decision { PREVIEW, CONFIRM, CANCEL }

	record PaymentRequest(String accountId, Decision decision)
	{
		public PaymentRequest
		{
			Objects.requireNonNull(accountId, "accountId");
			Objects.requireNonNull(decision, "decision");
		}
	}

	record PaymentContext(String accountId, String cardNumber,
		BigDecimal currentBalance)
	{
		public PaymentContext
		{
			Objects.requireNonNull(accountId, "accountId");
			Objects.requireNonNull(cardNumber, "cardNumber");
			Objects.requireNonNull(currentBalance, "currentBalance");
		}
	}
	record PaymentCommand(String accountId, String cardNumber,
		BigDecimal amount, long transactionId,
		LocalDateTime occurredAt)
	{
		public PaymentCommand
		{
			Objects.requireNonNull(accountId, "accountId");
			Objects.requireNonNull(cardNumber, "cardNumber");
			Objects.requireNonNull(amount, "amount");
			Objects.requireNonNull(occurredAt, "occurredAt");
		}
	}
	record PaymentResult(Outcome outcome,
		Optional<BigDecimal> previousBalance,
		Optional<BigDecimal> remainingBalance,
		Optional<Long> transactionId)
	{
		public PaymentResult
		{
			Objects.requireNonNull(outcome, "outcome");
			previousBalance = Objects.requireNonNull(previousBalance,
				"previousBalance");
			remainingBalance = Objects.requireNonNull(remainingBalance,
				"remainingBalance");
			transactionId = Objects.requireNonNull(transactionId,
				"transactionId");
		}
	}

	PaymentResult pay(PaymentRequest request);
}

@org.springframework.stereotype.Service
final class BillPaymentPaymentServiceJdbcAdapter implements BillPaymentPaymentService
{
	private static final String PREVIEW_SQL =
		"SELECT ACCT_CURR_BAL FROM ACCTDAT "
		+ "WHERE ACCT_ID = ?";
	private static final String CARD_SQL =
		"SELECT XREF_CARD_NUM FROM CCXREF "
		+ "WHERE XREF_ACCT_ID = ? ORDER BY XREF_CARD_NUM LIMIT 1";
	private static final String LOCK_SQL =
		"SELECT ACCT_CURR_BAL FROM ACCTDAT "
		+ "WHERE ACCT_ID = ? FOR UPDATE";
	private static final String LAST_TRANSACTION_SQL =
		"SELECT TRAN_ID FROM TRANSACT "
		+ "WHERE TRAN_ID > ? ORDER BY TRAN_ID DESC LIMIT 1";
	private static final String INSERT_TRANSACTION_SQL =
		"INSERT INTO TRANSACT (TRAN_ID, TRAN_TYPE_CD, "
		+ "TRAN_CAT_CD, TRAN_SOURCE, TRAN_DESC, TRAN_AMT, TRAN_MERCHANT_ID, "
		+ "TRAN_MERCHANT_NAME, TRAN_MERCHANT_CITY, TRAN_MERCHANT_ZIP, "
		+ "TRAN_CARD_NUM, TRAN_ORIG_TS, TRAN_PROC_TS) "
		+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	private static final String UPDATE_ACCOUNT_SQL =
		"UPDATE ACCTDAT SET ACCT_CURR_BAL = ? "
		+ "WHERE ACCT_ID = ?";

	private final javax.sql.DataSource dataSource;

	BillPaymentPaymentServiceJdbcAdapter(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	@Override
	public PaymentResult pay(PaymentRequest request)
	{
		if (request == null || !validAccountId(request.accountId()))
			return empty(Outcome.INVALID_REQUEST);
		String accountId = request.accountId().trim();
		return switch (request.decision())
		{
			case CANCEL -> empty(Outcome.CANCELLED);
			case PREVIEW -> preview(accountId);
			case CONFIRM -> confirm(accountId);
		};
	}

	private PaymentResult confirm(String accountId)
	{
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				long accountKey = Long.parseLong(accountId);
				BigDecimal balance = lockedBalance(connection, accountKey);
				if (balance == null)
				{
					connection.rollback();
					return empty(Outcome.ACCOUNT_NOT_FOUND);
				}
				if (balance.signum() <= 0)
				{
					connection.rollback();
					return empty(Outcome.PAYMENT_REJECTED);
				}
				String cardNumber = cardNumber(connection, accountKey);
				if (cardNumber == null)
				{
					connection.rollback();
					return empty(Outcome.ACCOUNT_NOT_FOUND);
				}
				long transactionId = nextTransactionId(connection);
				// The source stamps a payment with the region's local time to the
				// second: its timestamp layout zeroes the fraction.
				LocalDateTime occurredAt =
					LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
				insertTransaction(connection, transactionId, cardNumber,
					balance, occurredAt);
				try (PreparedStatement update = connection.prepareStatement(
					UPDATE_ACCOUNT_SQL))
				{
					update.setBigDecimal(1, BigDecimal.ZERO);
					update.setLong(2, accountKey);
					if (update.executeUpdate() != 1)
						throw new SQLException("account update count");
				}
				connection.commit();
				return new PaymentResult(Outcome.PAID,
					Optional.of(balance),
					Optional.of(BigDecimal.ZERO),
					Optional.of(transactionId));
			}
			catch (SQLException | RuntimeException failure)
			{
				try { connection.rollback(); }
				catch (SQLException rollbackFailure)
				{ failure.addSuppressed(rollbackFailure); }
				return empty(Outcome.UNAVAILABLE);
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(BillPaymentPaymentService.class).warn("relational payment service: confirm failed", failure);
			return empty(Outcome.UNAVAILABLE);
		}
	}

	private static BigDecimal lockedBalance(
		Connection connection, long accountId) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(LOCK_SQL))
		{
			statement.setLong(1, accountId);
			try (ResultSet row = statement.executeQuery())
			{ return row.next() ? row.getBigDecimal(1) : null; }
		}
	}

	private static String cardNumber(Connection connection,
		long accountId) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(CARD_SQL))
		{
			statement.setLong(1, accountId);
			try (ResultSet row = statement.executeQuery())
			{ return row.next() ? row.getString(1) : null; }
		}
	}

	private static long nextTransactionId(Connection connection)
		throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(
			LAST_TRANSACTION_SQL))
		{
			statement.setString(1, "");
			try (ResultSet row = statement.executeQuery())
			{ return row.next() ? Long.parseLong(row.getString(1)) + 1L : 1L; }
		}
	}

	private static void insertTransaction(Connection connection,
		long transactionId, String cardNumber, BigDecimal amount,
		LocalDateTime occurredAt) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(
			INSERT_TRANSACTION_SQL))
		{
			statement.setString(1, String.format("%016d", transactionId));
			statement.setString(2, "02");
			statement.setInt(3, 2);
			statement.setString(4, "POS TERM");
			statement.setString(5, "BILL PAYMENT - ONLINE");
			statement.setBigDecimal(6, amount);
			statement.setLong(7, 999999999);
			statement.setString(8, "BILL PAYMENT");
			statement.setString(9, "N/A");
			statement.setString(10, "N/A");
			statement.setString(11, cardNumber);
			// Both timestamp columns are real SQL TIMESTAMPs, not source text.
			statement.setObject(12, occurredAt);
			statement.setObject(13, occurredAt);
			if (statement.executeUpdate() != 1)
				throw new SQLException("transaction insert count");
		}
	}

	private PaymentResult preview(String accountId)
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(
				PREVIEW_SQL))
		{
			statement.setLong(1, Long.parseLong(accountId));
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next()) return empty(Outcome.ACCOUNT_NOT_FOUND);
				BigDecimal balance = row.getBigDecimal(1);
				if (balance == null) return empty(Outcome.UNAVAILABLE);
				if (balance.signum() <= 0) return empty(Outcome.PAYMENT_REJECTED);
				return new PaymentResult(Outcome.PREVIEW,
					Optional.of(balance), Optional.of(balance),
					Optional.empty());
			}
		}
		catch (SQLException | NumberFormatException failure)
		{
			return empty(Outcome.UNAVAILABLE);
		}
	}

	private static boolean validAccountId(String value)
	{
		if (value == null) return false;
		String candidate = value.trim();
		if (candidate.isEmpty() || candidate.length() > 11)
			return false;
		for (int index = 0; index < candidate.length(); ++index)
			if (!Character.isDigit(candidate.charAt(index))) return false;
		return true;
	}

	private static PaymentResult empty(Outcome outcome)
	{
		return new PaymentResult(outcome, Optional.empty(),
			Optional.empty(), Optional.empty());
	}
}
