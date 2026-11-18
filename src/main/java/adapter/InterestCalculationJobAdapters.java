package com.cardemo.batch.interest;

import java.io.IOException;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// The program's files over the application database, and its system
// transactions file. A JDBC failure becomes the permanent-error status "30".
final class InterestCalculationJobAdapters
{
	private InterestCalculationJobAdapters() { }

	private static final String PERMANENT_ERROR = "30";

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

	static final class TableFiles implements InterestCalculationJob.Lookups
	{
		private final javax.sql.DataSource dataSource;

		TableFiles(javax.sql.DataSource dataSource)
		{
			this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		}

		@Override
		public Optional<InterestCalculationAccountFileRepository.AccountSnapshot> account(long accountId)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT ACCT_ID, ACCT_ACTIVE_STATUS, ACCT_CURR_BAL, ACCT_CREDIT_LIMIT, ACCT_CASH_CREDIT_LIMIT, ACCT_OPEN_DATE, ACCT_EXPIRAION_DATE, ACCT_REISSUE_DATE, ACCT_CURR_CYC_CREDIT, ACCT_CURR_CYC_DEBIT, ACCT_ADDR_ZIP, ACCT_GROUP_ID FROM ACCTDAT WHERE ACCT_ID = ?"))
			{
				statement.setLong(1, accountId);
				try (ResultSet rows = statement.executeQuery())
				{
					if (!rows.next()) return Optional.empty();
					return Optional.of(new InterestCalculationAccountFileRepository.AccountSnapshot(
						rows.getLong(1), rows.getString(2), rows.getBigDecimal(3),
						rows.getBigDecimal(4), rows.getBigDecimal(5), date(rows.getString(6)),
						date(rows.getString(7)), date(rows.getString(8)), rows.getBigDecimal(9),
						rows.getBigDecimal(10), rows.getString(11), rows.getString(12)));
				}
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("account read failed", failure);
			}
		}

		private static LocalDate date(String text)
		{
			return text == null || text.isBlank() ? null : LocalDate.parse(text.strip());
		}

		// The cross-reference's alternate key, the account id; the first card
		// in card-number order when an account has several.
		@Override
		public Optional<String> cardOfAccount(long accountId)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT XREF_CARD_NUM FROM CCXREF WHERE XREF_ACCT_ID = ? "
						+ "ORDER BY XREF_CARD_NUM LIMIT 1"))
			{
				statement.setLong(1, accountId);
				try (ResultSet rows = statement.executeQuery())
				{
					return rows.next() ? Optional.of(rows.getString(1)) : Optional.empty();
				}
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("cross-reference read failed", failure);
			}
		}

		@Override
		public Optional<BigDecimal> rate(String groupId, String typeCode, long categoryCode)
		{
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT DIS_INT_RATE FROM DISCGRP WHERE DIS_ACCT_GROUP_ID = ? AND DIS_TRAN_TYPE_CD = ? AND DIS_TRAN_CAT_CD = ?"))
			{
				statement.setString(1, groupId == null ? "" : groupId.strip());
				statement.setString(2, typeCode);
				statement.setLong(3, categoryCode);
				try (ResultSet rows = statement.executeQuery())
				{
					return rows.next() ? Optional.of(rows.getBigDecimal(1)) : Optional.empty();
				}
			}
			catch (SQLException failure)
			{
				throw new IllegalStateException("disclosure group read failed", failure);
			}
		}

		InterestCalculationTcatbalFileRepository categoryBalances()
		{
			return new CategoryBalances(dataSource);
		}

		InterestCalculationXrefFileRepository crossReferences()
		{
			return new InterestCalculationXrefFileRepository()
			{
				@Override
				public AccessResult<Void> openXrefFile()
				{
					return status(reachable(dataSource, "CCXREF"));
				}

				@Override
				public AccessResult<Void> closeXrefFile()
				{
					return close();
				}

				@Override
				public AccessResult<Void> close()
				{
					return status(true);
				}

				private AccessResult<Void> status(boolean ok)
				{
					return new AccessResult<>(null, ok ? AccessOutcome.COMPLETED : AccessOutcome.REJECTED,
						ok ? "00" : "35");
				}
			};
		}

		InterestCalculationDiscgrpFileRepository disclosureGroups()
		{
			return new InterestCalculationDiscgrpFileRepository()
			{
				@Override
				public AccessResult<Void> openDiscgrpFile()
				{
					return status(reachable(dataSource, "DISCGRP"));
				}

				@Override
				public AccessResult<Void> closeDiscgrpFile()
				{
					return close();
				}

				@Override
				public AccessResult<Void> close()
				{
					return status(true);
				}

				private AccessResult<Void> status(boolean ok)
				{
					return new AccessResult<>(null, ok ? AccessOutcome.COMPLETED : AccessOutcome.REJECTED,
						ok ? "00" : "35");
				}
			};
		}

		InterestCalculationAccountFileRepository accounts()
		{
			return new InterestCalculationAccountFileRepository()
			{
				@Override
				public AccessResult<Void> openAccountFile()
				{
					return status(reachable(dataSource, "ACCTDAT"), "35");
				}

				@Override
				public AccessResult<Void> update(AccountSnapshot value)
				{
					try (Connection connection = dataSource.getConnection();
						PreparedStatement statement = connection.prepareStatement(
							"UPDATE ACCTDAT SET ACCT_CURR_BAL = ?, "
								+ "ACCT_CURR_CYC_CREDIT = ?, ACCT_CURR_CYC_DEBIT = ? "
								+ "WHERE ACCT_ID = ?"))
					{
						statement.setBigDecimal(1, value.acctCurrBal());
						statement.setBigDecimal(2, value.acctCurrCycCredit());
						statement.setBigDecimal(3, value.acctCurrCycDebit());
						statement.setLong(4, value.acctId());
						return status(statement.executeUpdate() == 1, "23");
					}
					catch (SQLException failure)
					{
						return status(false, PERMANENT_ERROR);
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
					return status(true, "00");
				}

				private AccessResult<Void> status(boolean ok, String failure)
				{
					return new AccessResult<>(null, ok ? AccessOutcome.COMPLETED : AccessOutcome.REJECTED,
						ok ? "00" : failure);
				}
			};
		}
	}

	// The category balance file read sequentially, in key order.
	static final class CategoryBalances implements InterestCalculationTcatbalFileRepository
	{
		private final javax.sql.DataSource dataSource;
		private Iterator<TransactionCategoryBalance> records;

		CategoryBalances(javax.sql.DataSource dataSource)
		{
			this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		}

		@Override
		public AccessResult<Void> openTcatbalFile()
		{
			List<TransactionCategoryBalance> all = new ArrayList<>();
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
					"SELECT TRANCAT_ACCT_ID, TRANCAT_TYPE_CD, TRANCAT_CD, TRAN_CAT_BAL FROM TRAN_CAT_BAL ORDER BY TRANCAT_ACCT_ID, TRANCAT_TYPE_CD, TRANCAT_CD");
				ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
					all.add(new TransactionCategoryBalance(rows.getLong(1), rows.getString(2),
						rows.getLong(3), rows.getBigDecimal(4)));
			}
			catch (SQLException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED, "35");
			}
			records = all.iterator();
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
		}

		@Override
		public ReadResult<TransactionCategoryBalance> readNext()
		{
			return records.hasNext() ? new ReadResult<>(records.next(), ReadOutcome.RECORD, "00")
				: new ReadResult<>(null, ReadOutcome.END_OF_DATA, "10");
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

	// The system transactions file (the JCL's new SYSTRAN generation): one
	// fixed-width host record per interest transaction, in the transaction
	// record's layout.
	static final class SystemTransactions implements InterestCalculationTransactFileRepository
	{
		private final Path path;
		private final Charset charset;
		private OutputStream stream;

		SystemTransactions(Path path, Charset charset)
		{
			this.path = Objects.requireNonNull(path, "path");
			this.charset = Objects.requireNonNull(charset, "charset");
		}

		@Override
		public AccessResult<Void> openTransactFile()
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
		public AccessResult<Void> write(TransactionSnapshot value)
		{
			String image = text(value.tranId(), 16) + text(value.tranTypeCd(), 2)
				+ String.format("%04d", value.tranCatCd()) + text(value.tranSource(), 10)
				+ text(value.tranDesc(), 100) + InterestCalculationJob.zoned(value.tranAmt(), 11)
				+ String.format("%09d", value.tranMerchantId()) + text(value.tranMerchantName(), 50)
				+ text(value.tranMerchantCity(), 50) + text(value.tranMerchantZip(), 10)
				+ text(value.tranCardNum(), 16) + text(value.tranOrigTs(), 26)
				+ text(value.tranProcTs(), 26) + " ".repeat(20);
			try
			{
				stream.write(image.getBytes(charset));
				return new AccessResult<>(null, AccessOutcome.COMPLETED, "00");
			}
			catch (IOException failure)
			{
				return new AccessResult<>(null, AccessOutcome.REJECTED, PERMANENT_ERROR);
			}
		}

		private static String text(String value, int width)
		{
			String text = value == null ? "" : value;
			return text.length() >= width ? text.substring(0, width)
				: text + " ".repeat(width - text.length());
		}

		@Override
		public AccessResult<Void> closeTransactFile()
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
}
