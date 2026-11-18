package com.cardemo.batch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

// The program's files over the application database and the packaged seed.
// A JDBC failure becomes the source's permanent-error file status, "30".
final class DailyTransactionPostingJobAdapters
{
	private DailyTransactionPostingJobAdapters() { }

	private static final String PERMANENT_ERROR = "30";

	// The daily transaction file: fixed-width host records from the packaged
	// seed, read in order.
	static final class SeedInput implements DailyTransactionPostingDalytranFileRepository,
		DailyTransactionPostingJob.RecordImageSource
	{
		private final String resource;
		private final Charset charset;
		private InputStream stream;
		private String lastImage = "";

		SeedInput(String resource, Charset charset)
		{
			this.resource = Objects.requireNonNull(resource, "resource");
			this.charset = Objects.requireNonNull(charset, "charset");
		}

		@Override
		public AccessResult<Void> openDalytranFile()
		{
			stream = getClass().getClassLoader().getResourceAsStream(resource);
			return stream == null ? new AccessResult<>(null, AccessOutcome.REJECTED, "35")
				: new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
		}

		@Override
		public ReadResult<DailyTransaction> readNext()
		{
			try
			{
				byte[] bytes = stream.readNBytes(DailyTransaction.RECORD_LENGTH);
				if (bytes.length == 0)
					return new ReadResult<>(null, ReadOutcome.END_OF_DATA, "10");
				if (bytes.length != DailyTransaction.RECORD_LENGTH)
					return new ReadResult<>(null, ReadOutcome.FAILED, "04");
				lastImage = new String(bytes, charset);
				return new ReadResult<>(DailyTransaction.decode(lastImage),
					ReadOutcome.RECORD, "00");
			}
			catch (IOException failure)
			{
				return new ReadResult<>(null, ReadOutcome.FAILED, PERMANENT_ERROR);
			}
		}

		@Override
		public String lastRecordImage()
		{
			return lastImage;
		}

		@Override
		public AccessResult<Void> closeDalytranFile()
		{
			return close();
		}

		@Override
		public AccessResult<Void> close()
		{
			try
			{
				if (stream != null) stream.close();
				return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
			}
			catch (IOException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED, PERMANENT_ERROR);
			}
		}
	}

	// Opening a table-backed file checks that its table is reachable.
	private static boolean reachable(javax.sql.DataSource dataSource, String table)
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(
				"SELECT 1 FROM " + table + " WHERE 1 = 0"))
		{
			statement.executeQuery().close();
			return true;
		}
		catch (SQLException failure)
		{
			return false;
		}
	}

	static final class PostedTransactions implements DailyTransactionPostingTransactFileRepository
	{
		private static final DateTimeFormatter ORIGIN =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
		private static final DateTimeFormatter PROCESSED =
			DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS");
		private final javax.sql.DataSource dataSource;

		PostedTransactions(javax.sql.DataSource dataSource)
		{
			this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		}

		@Override
		public AccessResult<Void> openTransactFile()
		{
			return reachable(dataSource, "TRANSACT")
				? new AccessResult<>(null, AccessOutcome.COMPLETED, "00")
				: new AccessResult<>(null, AccessOutcome.REJECTED, "35");
		}

		@Override
		public AccessResult<Void> write(TransactionSnapshot value)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"INSERT INTO TRANSACT (TRAN_ID, TRAN_TYPE_CD, TRAN_CAT_CD, TRAN_SOURCE, TRAN_DESC, TRAN_AMT, TRAN_MERCHANT_ID, TRAN_MERCHANT_NAME, TRAN_MERCHANT_CITY, TRAN_MERCHANT_ZIP, TRAN_CARD_NUM, TRAN_ORIG_TS, TRAN_PROC_TS) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))
			{
				statement.setString(1, value.tranId());
				statement.setString(2, value.tranTypeCd());
				statement.setLong(3, value.tranCatCd());
				statement.setString(4, value.tranSource().strip());
				statement.setString(5, value.tranDesc().strip());
				statement.setBigDecimal(6, value.tranAmt());
				statement.setLong(7, value.tranMerchantId());
				statement.setString(8, value.tranMerchantName().strip());
				statement.setString(9, value.tranMerchantCity().strip());
				statement.setString(10, value.tranMerchantZip().strip());
				statement.setString(11, value.tranCardNum());
				statement.setObject(12, timestamp(value.tranOrigTs(), ORIGIN));
				statement.setObject(13, timestamp(value.tranProcTs(), PROCESSED));
				statement.executeUpdate();
				return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
			}
			catch (SQLException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED,
					"23505".equals(failure.getSQLState()) ? "22" : PERMANENT_ERROR);
			}
		}

		private static LocalDateTime timestamp(String text, DateTimeFormatter format)
		{
			return text == null || text.isBlank() ? null
				: LocalDateTime.parse(text.strip(), format);
		}

		@Override
		public AccessResult<Void> closeTransactFile()
		{
			return close();
		}

		@Override
		public AccessResult<Void> close()
		{
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
		}
	}

	static final class CrossReferences implements DailyTransactionPostingXrefFileRepository
	{
		private final javax.sql.DataSource dataSource;

		CrossReferences(javax.sql.DataSource dataSource)
		{
			this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		}

		@Override
		public AccessResult<Void> openXrefFile()
		{
			return reachable(dataSource, "CCXREF")
				? new AccessResult<>(null, AccessOutcome.COMPLETED, "00")
				: new AccessResult<>(null, AccessOutcome.REJECTED, "35");
		}

		@Override
		public LookupResult findByKey(String key)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT XREF_CARD_NUM, XREF_CUST_ID, XREF_ACCT_ID FROM CCXREF WHERE XREF_CARD_NUM = ?"))
			{
				statement.setString(1, key);
				try (ResultSet rows = statement.executeQuery())
				{
					if (!rows.next()) return missing();
					return new LookupResult(LookupOutcome.FOUND, Optional.of(
						new CardCrossReferenceSnapshot(rows.getString(1),
							rows.getLong(2), rows.getLong(3))), "");
				}
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("cross-reference lookup failed", failure);
			}
		}

		@Override
		public AccessResult<Void> closeXrefFile()
		{
			return close();
		}

		@Override
		public AccessResult<Void> close()
		{
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
		}
	}

	static final class Accounts implements DailyTransactionPostingAccountFileRepository
	{
		private final javax.sql.DataSource dataSource;

		Accounts(javax.sql.DataSource dataSource)
		{
			this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		}

		@Override
		public AccessResult<Void> openAccountFile()
		{
			return reachable(dataSource, "ACCTDAT")
				? new AccessResult<>(null, AccessOutcome.COMPLETED, "00")
				: new AccessResult<>(null, AccessOutcome.REJECTED, "35");
		}

		@Override
		public LookupResult findByKey(long key)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT ACCT_ID, ACCT_ACTIVE_STATUS, ACCT_CURR_BAL, ACCT_CREDIT_LIMIT, ACCT_CASH_CREDIT_LIMIT, ACCT_OPEN_DATE, ACCT_EXPIRAION_DATE, ACCT_REISSUE_DATE, ACCT_CURR_CYC_CREDIT, ACCT_CURR_CYC_DEBIT, ACCT_ADDR_ZIP, ACCT_GROUP_ID FROM ACCTDAT WHERE ACCT_ID = ?"))
			{
				statement.setLong(1, key);
				try (ResultSet rows = statement.executeQuery())
				{
					if (!rows.next()) return missing();
					return new LookupResult(LookupOutcome.FOUND, Optional.of(new AccountSnapshot(
						rows.getLong(1), rows.getString(2), rows.getBigDecimal(3),
						rows.getBigDecimal(4), rows.getBigDecimal(5), date(rows.getString(6)),
						date(rows.getString(7)), date(rows.getString(8)), rows.getBigDecimal(9),
						rows.getBigDecimal(10), rows.getString(11), rows.getString(12))), "");
				}
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("account lookup failed", failure);
			}
		}

		private static LocalDate date(String text)
		{
			return text == null || text.isBlank() ? null : LocalDate.parse(text.strip());
		}

		// The amount goes to the current balance, and to the cycle credit when
		// it is not negative, else to the cycle debit (the source's 2800).
		@Override
		public BalanceUpdateResult applySignedBalanceDelta(AccountSnapshot current,
			BigDecimal amount)
		{
			boolean credit = amount.signum() >= 0;
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"UPDATE ACCTDAT SET ACCT_CURR_BAL = ACCT_CURR_BAL + ?, "
						+ (credit ? "ACCT_CURR_CYC_CREDIT = ACCT_CURR_CYC_CREDIT + ?"
							: "ACCT_CURR_CYC_DEBIT = ACCT_CURR_CYC_DEBIT + ?")
						+ " WHERE ACCT_ID = ?"))
			{
				statement.setBigDecimal(1, amount);
				statement.setBigDecimal(2, amount);
				statement.setLong(3, current.acctId());
				if (statement.executeUpdate() == 0)
					return new BalanceUpdateResult(null, BalanceUpdateOutcome.NOT_FOUND,
						"ACCOUNT RECORD NOT FOUND");
				return new BalanceUpdateResult(findByKey(current.acctId()).value().orElseThrow(),
					BalanceUpdateOutcome.UPDATED, "");
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("account rewrite failed", failure);
			}
		}

		@Override
		public AccessResult<Void> closeAccountFile()
		{
			return close();
		}

		@Override
		public AccessResult<Void> close()
		{
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
		}
	}

	static final class CategoryBalances implements DailyTransactionPostingTcatbalFileRepository,
		DailyTransactionPostingJob.CategoryBalanceLookup
	{
		private final javax.sql.DataSource dataSource;

		CategoryBalances(javax.sql.DataSource dataSource)
		{
			this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		}

		@Override
		public AccessResult<Void> openTcatbalFile()
		{
			return reachable(dataSource, "TRAN_CAT_BAL")
				? new AccessResult<>(null, AccessOutcome.COMPLETED, "00")
				: new AccessResult<>(null, AccessOutcome.REJECTED, "35");
		}

		@Override
		public Optional<TransactionCategoryBalanceSnapshot> findByKey(long accountId,
			String typeCode, long categoryCode)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT TRAN_CAT_BAL FROM TRAN_CAT_BAL WHERE TRANCAT_ACCT_ID = ? AND TRANCAT_TYPE_CD = ? AND TRANCAT_CD = ?"))
			{
				statement.setLong(1, accountId);
				statement.setString(2, typeCode);
				statement.setLong(3, categoryCode);
				try (ResultSet rows = statement.executeQuery())
				{
					return rows.next() ? Optional.of(new TransactionCategoryBalanceSnapshot(accountId,
						typeCode, categoryCode, rows.getBigDecimal(1))) : Optional.empty();
				}
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("category balance lookup failed", failure);
			}
		}

		@Override
		public AccessResult<Void> write(TransactionCategoryBalanceSnapshot value)
		{
			return execute("INSERT INTO TRAN_CAT_BAL (TRANCAT_ACCT_ID, TRANCAT_TYPE_CD, TRANCAT_CD, TRAN_CAT_BAL) VALUES (?, ?, ?, ?)",
				value, false);
		}

		@Override
		public AccessResult<Void> update(TransactionCategoryBalanceSnapshot value)
		{
			return execute("UPDATE TRAN_CAT_BAL SET TRAN_CAT_BAL = ? WHERE TRANCAT_ACCT_ID = ? AND TRANCAT_TYPE_CD = ? AND TRANCAT_CD = ?",
				value, true);
		}

		private AccessResult<Void> execute(String sql, TransactionCategoryBalanceSnapshot value,
			boolean valueFirst)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql))
			{
				int key = valueFirst ? 2 : 1;
				statement.setBigDecimal(valueFirst ? 1 : 4, value.tranCatBal());
				statement.setLong(key, value.trancatAcctId());
				statement.setString(key + 1, value.trancatTypeCd());
				statement.setLong(key + 2, value.trancatCd());
				return statement.executeUpdate() == 1
					? new AccessResult<>(null, AccessOutcome.COMPLETED, "00")
					: new AccessResult<>(null, AccessOutcome.REJECTED, "23");
			}
			catch (SQLException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED, PERMANENT_ERROR);
			}
		}

		@Override
		public AccessResult<Void> closeTcatbalFile()
		{
			return close();
		}

		@Override
		public AccessResult<Void> close()
		{
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
		}
	}

	// The daily rejects file: each reject is the input image followed by the
	// validation trailer, written as one fixed-width host record.
	static final class RejectFile implements DailyTransactionPostingDalyrejsFileRepository
	{
		private final Path path;
		private final Charset charset;
		private OutputStream stream;

		RejectFile(Path path, Charset charset)
		{
			this.path = Objects.requireNonNull(path, "path");
			this.charset = Objects.requireNonNull(charset, "charset");
		}

		@Override
		public AccessResult<Void> openDalyrejsFile()
		{
			try
			{
				stream = Files.newOutputStream(path);
				return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
			}
			catch (IOException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED, PERMANENT_ERROR);
			}
		}

		@Override
		public AccessResult<Void> write(RejectedTransactionSnapshot value)
		{
			try
			{
				stream.write((value.rejectTranData() + value.validationTrailer()).getBytes(charset));
				return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
			}
			catch (IOException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED, PERMANENT_ERROR);
			}
		}

		@Override
		public AccessResult<Void> closeDalyrejsFile()
		{
			try
			{
				if (stream != null) stream.close();
				return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
			}
			catch (IOException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED, PERMANENT_ERROR);
			}
		}
	}
}
