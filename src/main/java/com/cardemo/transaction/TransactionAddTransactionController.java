package com.cardemo.transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/transactions")
public final class TransactionAddTransactionController
{
	public enum Outcome { CREATED, INVALID, DUPLICATE, FAILED }

	public record CreateRequest(long accountId, String cardNumber,
		String typeCode, long categoryCode, String source, String description,
		BigDecimal amount, String originalDate, String processedDate,
		long merchantId, String merchantName, String merchantCity,
		String merchantZip)
	{
		public CreateRequest
		{
			Objects.requireNonNull(cardNumber, "cardNumber");
			Objects.requireNonNull(typeCode, "typeCode");
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(description, "description");
			Objects.requireNonNull(amount, "amount");
			Objects.requireNonNull(originalDate, "originalDate");
			Objects.requireNonNull(processedDate, "processedDate");
			Objects.requireNonNull(merchantName, "merchantName");
			Objects.requireNonNull(merchantCity, "merchantCity");
			Objects.requireNonNull(merchantZip, "merchantZip");
		}
	}

	public record CreateResponse(Outcome outcome,
		Optional<String> transactionId, String message)
	{
		public CreateResponse
		{
			Objects.requireNonNull(outcome, "outcome");
			transactionId = Objects.requireNonNull(transactionId,
				"transactionId");
			Objects.requireNonNull(message, "message");
			if ((outcome == Outcome.CREATED) != transactionId.isPresent())
				throw new IllegalArgumentException(
					"transactionId must match outcome");
		}
	}

	public record ValidationResult(boolean accepted, String message)
	{
		public ValidationResult
		{
			Objects.requireNonNull(message, "message");
		}
	}

	public interface AccountCardValidationPort
	{
		ValidationResult validate(long accountId, String cardNumber);
	}

	public interface DateValidationPort
	{
		ValidationResult validate(String date);
	}

	public record TransactionDraft(String cardNumber, String typeCode,
		long categoryCode, String source, String description,
		BigDecimal amount, String originalDate, String processedDate,
		long merchantId, String merchantName, String merchantCity,
		String merchantZip) { }

	public interface AppendPort
	{
		CreateResponse append(TransactionDraft draft);
	}

	private final AccountCardValidationPort accountCards;
	private final DateValidationPort dates;
	private final AppendPort transactions;

	public TransactionAddTransactionController(AccountCardValidationPort accountCards,
		DateValidationPort dates, AppendPort transactions)
	{
		this.accountCards = Objects.requireNonNull(accountCards,
			"accountCards");
		this.dates = Objects.requireNonNull(dates, "dates");
		this.transactions = Objects.requireNonNull(transactions,
			"transactions");
	}

	@PostMapping
	public CreateResponse create(@RequestBody CreateRequest request)
	{
		CreateRequest value = Objects.requireNonNull(request, "request");
		ValidationResult shapeResult = validateSourceShape(value);
		if (!shapeResult.accepted()) return invalid(shapeResult.message());
		ValidationResult keyResult = accountCards.validate(value.accountId(),
			value.cardNumber());
		if (!keyResult.accepted()) return invalid(keyResult.message());
		ValidationResult originalResult = dates.validate(value.originalDate());
		if (!originalResult.accepted()) return invalid(originalResult.message());
		ValidationResult processedResult = dates.validate(value.processedDate());
		if (!processedResult.accepted()) return invalid(processedResult.message());
		return transactions.append(new TransactionDraft(value.cardNumber(),
			value.typeCode(), value.categoryCode(), value.source(),
			value.description(), value.amount(), value.originalDate(),
			value.processedDate(), value.merchantId(), value.merchantName(),
			value.merchantCity(), value.merchantZip()));
	}

	private static ValidationResult validateSourceShape(CreateRequest value)
	{
		if (value.accountId() < 0L || value.accountId() > 99999999999L)
			return rejected("Account ID must contain at most 11 digits.");
		if (value.cardNumber().length() != 16)
			return rejected("Card number must contain exactly 16 characters.");
		if (!isUnsignedDigits(value.typeCode()) ||
			value.typeCode().length() > 2)
			return rejected("Type code must contain at most 2 digits.");
		if (value.categoryCode() < 0L ||
			value.categoryCode() > 9999L)
			return rejected("Category code must contain at most 4 digits.");
		if (value.source().isBlank() ||
			value.source().length() > 10)
			return rejected("Source must contain at most 10 characters.");
		if (value.description().isBlank() ||
			value.description().length() > 60)
			return rejected("Description must contain at most 60 characters.");
		if (value.amount().scale() > 2 ||
			value.amount().abs().compareTo(new BigDecimal(
				"999999999.99")) > 0)
			return rejected("Amount must have at most 9 integer digits and 2 decimal places.");
		if (value.merchantId() < 0L ||
			value.merchantId() > 999999999L)
			return rejected("Merchant ID must contain at most 9 digits.");
		if (value.merchantName().isBlank() ||
			value.merchantName().length() > 30)
			return rejected("Merchant name must contain at most 30 characters.");
		if (value.merchantCity().isBlank() ||
			value.merchantCity().length() > 25)
			return rejected("Merchant city must contain at most 25 characters.");
		if (value.merchantZip().isBlank() ||
			value.merchantZip().length() > 10)
			return rejected("Merchant postal code must contain at most 10 characters.");
		return new ValidationResult(true, "");
	}

	private static boolean isUnsignedDigits(String value)
	{
		if (value.isEmpty()) return false;
		for (int index = 0; index < value.length(); ++index)
			if (!Character.isDigit(value.charAt(index))) return false;
		return true;
	}

	private static ValidationResult rejected(String message)
	{
		return new ValidationResult(false, message);
	}

	private static CreateResponse invalid(String message)
	{
		return new CreateResponse(Outcome.INVALID, Optional.empty(),
			Objects.requireNonNull(message, "message"));
	}
}

@Repository
class TransactionAddTransactionControllerRelationalRepository
	implements TransactionAddTransactionController.AppendPort
{
	private static final String PREDECESSOR_SQL =
		"SELECT TRAN_ID FROM TRANSACT "
		+ "ORDER BY TRAN_ID DESC FETCH FIRST 1 ROW ONLY";
	private static final String INSERT_SQL =
		"INSERT INTO TRANSACT (TRAN_ID, TRAN_TYPE_CD, "
		+ "TRAN_CAT_CD, TRAN_SOURCE, "
		+ "TRAN_DESC, TRAN_AMT, "
		+ "TRAN_MERCHANT_ID, TRAN_MERCHANT_NAME, "
		+ "TRAN_MERCHANT_CITY, TRAN_MERCHANT_ZIP, "
		+ "TRAN_CARD_NUM, TRAN_ORIG_TS, "
		+ "TRAN_PROC_TS) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private final javax.sql.DataSource dataSource;

	public TransactionAddTransactionControllerRelationalRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	@Override
	public TransactionAddTransactionController.CreateResponse append(
		TransactionAddTransactionController.TransactionDraft draft)
	{
		TransactionAddTransactionController.TransactionDraft value = Objects.requireNonNull(
			draft, "draft");
		try (Connection connection = dataSource.getConnection())
		{
			String transactionId = successor(connection);
			try (PreparedStatement statement =
				connection.prepareStatement(INSERT_SQL))
			{
				statement.setString(1, transactionId);
				statement.setString(2, value.typeCode());
				statement.setLong(3, value.categoryCode());
				statement.setString(4, value.source());
				statement.setString(5, value.description());
				statement.setBigDecimal(6, value.amount());
				statement.setLong(7, value.merchantId());
				statement.setString(8, value.merchantName());
				statement.setString(9, value.merchantCity());
				statement.setString(10, value.merchantZip());
				statement.setString(11, value.cardNumber());
				// Both timestamp columns are real SQL TIMESTAMPs, not source text.
				statement.setObject(12, enteredDateTimestamp(value.originalDate()));
				statement.setObject(13, enteredDateTimestamp(value.processedDate()));
				if (statement.executeUpdate() != 1)
					return failed();
				return new TransactionAddTransactionController.CreateResponse(
					TransactionAddTransactionController.Outcome.CREATED,
					Optional.of(transactionId),
					"Transaction added.");
			}
		}
		catch (SQLIntegrityConstraintViolationException duplicate)
		{
			return new TransactionAddTransactionController.CreateResponse(
				TransactionAddTransactionController.Outcome.DUPLICATE, Optional.empty(),
				"Transaction identifier already exists.");
		}
		catch (SQLException | ArithmeticException |
			NumberFormatException failure)
		{
			return failed();
		}
	}

	private static String successor(Connection connection)
		throws SQLException
	{
		try (PreparedStatement statement =
			connection.prepareStatement(PREDECESSOR_SQL);
			ResultSet row = statement.executeQuery())
		{
			long predecessor = row.next()
				? Long.parseLong(row.getString(1).trim()) : 0L;
			return String.format(Locale.ROOT, "%016d",
				Math.addExact(predecessor, 1L));
		}
	}

	// The screen takes these as dates with no time of day (validated above as
	// ISO yyyy-MM-dd). A timestamp needs a time, so the entered date is stored
	// as midnight of that day; the source itself carries no time here.
	private static LocalDateTime enteredDateTimestamp(String date)
	{
		return LocalDate.parse(date).atStartOfDay();
	}

	private static TransactionAddTransactionController.CreateResponse failed()
	{
		return new TransactionAddTransactionController.CreateResponse(TransactionAddTransactionController.Outcome.FAILED,
			Optional.empty(), "Unable to add transaction.");
	}
}

@org.springframework.stereotype.Repository
class TransactionAddTransactionControllerAccountCardValidationAdapter
	implements TransactionAddTransactionController.AccountCardValidationPort
{
	private final javax.sql.DataSource dataSource;

	public TransactionAddTransactionControllerAccountCardValidationAdapter(
		javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	@Override
	public TransactionAddTransactionController.ValidationResult validate(long accountId,
		String cardNumber)
	{
		String sql = "SELECT 1 FROM ACCTDAT A "
			+ "JOIN CARDDAT C ON C.CARD_ACCT_ID = "
			+ "A.ACCT_ID WHERE A.ACCT_ID = ? "
			+ "AND C.CARD_NUM = ?";
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setLong(1, accountId);
			statement.setString(2, Objects.requireNonNull(cardNumber,
				"cardNumber"));
			try (ResultSet rows = statement.executeQuery())
			{
				boolean accepted = rows.next();
				return new TransactionAddTransactionController.ValidationResult(accepted,
					accepted ? "" : "Account and card do not match.");
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(TransactionAddTransactionController.class).warn("relational transaction create controller: validate failed", failure);
			return new TransactionAddTransactionController.ValidationResult(false,
				"Unable to add transaction.");
		}
	}
}

@org.springframework.stereotype.Component
final class TransactionAddTransactionControllerDateValidationAdapter
	implements TransactionAddTransactionController.DateValidationPort
{
	@Override
	public TransactionAddTransactionController.ValidationResult validate(String date)
	{
		try
		{
			LocalDate.parse(Objects.requireNonNull(date,
				"date"));
			return new TransactionAddTransactionController.ValidationResult(true, "");
		}
		catch (DateTimeParseException failure)
		{
			return new TransactionAddTransactionController.ValidationResult(false,
				"Enter a valid date.");
		}
	}
}
