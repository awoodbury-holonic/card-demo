package com.cardemo.batch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// The program's files: the packaged daily seed, and the table-backed files it
// opens, in source order. A file that cannot be opened reports status "35".
final class DailyTransactionVerificationJobAdapters
{
	private DailyTransactionVerificationJobAdapters() { }

	static final class SeedImages implements DailyTransactionVerificationJob.RecordImages
	{
		private final String resource;
		private final int length;
		private final Charset charset;
		private InputStream stream;

		SeedImages(String resource, int length, Charset charset)
		{
			this.resource = Objects.requireNonNull(resource, "resource");
			this.length = length;
			this.charset = Objects.requireNonNull(charset, "charset");
		}

		@Override
		public String open()
		{
			stream = getClass().getClassLoader().getResourceAsStream(resource);
			return stream == null ? "35" : "00";
		}

		// The next record image; null at end of file, empty for a short record.
		@Override
		public String next()
		{
			try
			{
				byte[] bytes = stream.readNBytes(length);
				if (bytes.length == 0) return null;
				return bytes.length == length ? new String(bytes, charset) : "";
			}
			catch (IOException failure)
			{
				return "";
			}
		}

		@Override
		public String close()
		{
			try
			{
				if (stream != null) stream.close();
				return "00";
			}
			catch (IOException failure)
			{
				return "30";
			}
		}
	}

	record TableFile(javax.sql.DataSource dataSource, String table, String openMessage,
		String closeMessage)
	{
		String open()
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT 1 FROM " + table + " WHERE 1 = 0"))
			{
				statement.executeQuery().close();
				return "00";
			}
			catch (SQLException failure)
			{
				return "35";
			}
		}

		String close()
		{
			return "00";
		}
	}

	static List<TableFile> tables(javax.sql.DataSource dataSource)
	{
		return List.of(
			new TableFile(dataSource, "CUSTDAT", "ERROR OPENING CUSTOMER FILE",
				"ERROR CLOSING CUSTOMER FILE"),
			new TableFile(dataSource, "CCXREF", "ERROR OPENING CROSS REF FILE",
				"ERROR CLOSING CROSS REF FILE"),
			new TableFile(dataSource, "CARDDAT", "ERROR OPENING CARD FILE",
				"ERROR CLOSING CARD FILE"),
			new TableFile(dataSource, "ACCTDAT", "ERROR OPENING ACCOUNT FILE",
				"ERROR CLOSING ACCOUNT FILE"),
			new TableFile(dataSource, "TRANSACT", "ERROR OPENING TRANSACTION FILE",
				"ERROR CLOSING TRANSACTION FILE"));
	}

	static final class Lookups implements DailyTransactionVerificationJob.Lookups
	{
		private final javax.sql.DataSource dataSource;

		Lookups(javax.sql.DataSource dataSource)
		{
			this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		}

		@Override
		public Optional<DailyTransactionVerificationJob.CrossReference> crossReference(String cardNumber)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT XREF_CARD_NUM, XREF_CUST_ID, XREF_ACCT_ID FROM CCXREF WHERE XREF_CARD_NUM = ?"))
			{
				statement.setString(1, cardNumber);
				try (ResultSet rows = statement.executeQuery())
				{
					return rows.next() ? Optional.of(new DailyTransactionVerificationJob.CrossReference(
						rows.getString(1), rows.getLong(2), rows.getLong(3))) : Optional.empty();
				}
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("cross-reference read failed", failure);
			}
		}

		@Override
		public boolean accountExists(long accountId)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT 1 FROM ACCTDAT WHERE ACCT_ID = ?"))
			{
				statement.setLong(1, accountId);
				try (ResultSet rows = statement.executeQuery())
				{
					return rows.next();
				}
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("account read failed", failure);
			}
		}
	}
}
