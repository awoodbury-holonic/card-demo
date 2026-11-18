package com.cardemo.batch.account;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

// The account file over the application database, read in key order, and the
// program's sequential output files.
final class AccountFileDistributionJobAdapters
{
	private AccountFileDistributionJobAdapters() { }

	static final class Accounts implements AccountFileDistributionAcctfileFileRepository
	{
		private final javax.sql.DataSource dataSource;
		private Iterator<Account> records;

		Accounts(javax.sql.DataSource dataSource)
		{
			this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		}

		@Override
		public AccessResult<Void> openAcctfileFile()
		{
			List<Account> all = new ArrayList<>();
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT ACCT_ID, ACCT_ACTIVE_STATUS, ACCT_CURR_BAL, ACCT_CREDIT_LIMIT, ACCT_CASH_CREDIT_LIMIT, ACCT_OPEN_DATE, ACCT_EXPIRAION_DATE, ACCT_REISSUE_DATE, ACCT_CURR_CYC_CREDIT, ACCT_CURR_CYC_DEBIT, ACCT_ADDR_ZIP, ACCT_GROUP_ID FROM ACCTDAT ORDER BY ACCT_ID");
				ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
					all.add(new Account(rows.getLong(1), rows.getString(2),
						rows.getBigDecimal(3), rows.getBigDecimal(4), rows.getBigDecimal(5),
						date(rows.getString(6)), date(rows.getString(7)), date(rows.getString(8)),
						rows.getBigDecimal(9), rows.getBigDecimal(10), rows.getString(11),
						rows.getString(12)));
			}
			catch (SQLException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED, "35");
			}
			records = all.iterator();
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
		}

		private static LocalDate date(String text)
		{
			return text == null || text.isBlank() ? null : LocalDate.parse(text.strip());
		}

		@Override
		public ReadResult<Account> readNext()
		{
			return records.hasNext() ? new ReadResult<>(records.next(), ReadOutcome.RECORD, "00")
				: new ReadResult<>(null, ReadOutcome.END_OF_DATA, "10");
		}

		@Override
		public AccessResult<Void> closeAcctfileFile()
		{
			return close();
		}

		@Override
		public AccessResult<Void> close()
		{
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
		}
	}

	// A sequential output file of host records, written as given.
	static final class RecordFile
	{
		private final Path path;
		private OutputStream stream;

		RecordFile(Path path)
		{
			this.path = Objects.requireNonNull(path, "path");
		}

		String open()
		{
			try
			{
				stream = Files.newOutputStream(path);
				return "00";
			}
			catch (IOException failure)
			{
				return "30";
			}
		}

		String write(byte[]... parts)
		{
			try
			{
				for (byte[] part : parts)
					stream.write(part);
				return "00";
			}
			catch (IOException failure)
			{
				return "30";
			}
		}

		void close()
		{
			try
			{
				if (stream != null) stream.close();
			}
			catch (IOException failure)
			{
				// The source never closes its outputs explicitly.
			}
		}
	}
}
