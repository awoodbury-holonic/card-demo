package com.cardemo.batch.account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@org.springframework.stereotype.Repository
public class AccountJdbcRepository
{
	private static final String UPSERT =
		"MERGE INTO ACCTDAT (ACCT_ID,ACCT_ACTIVE_STATUS,ACCT_CURR_BAL,ACCT_CREDIT_LIMIT,ACCT_CASH_CREDIT_LIMIT,ACCT_OPEN_DATE,ACCT_EXPIRAION_DATE,ACCT_REISSUE_DATE,ACCT_CURR_CYC_CREDIT,ACCT_CURR_CYC_DEBIT,ACCT_ADDR_ZIP,ACCT_GROUP_ID) " +
		"KEY(ACCT_ID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String SELECT_AFTER =
		"SELECT ACCT_ID,ACCT_ACTIVE_STATUS,ACCT_CURR_BAL,ACCT_CREDIT_LIMIT,ACCT_CASH_CREDIT_LIMIT,ACCT_OPEN_DATE,ACCT_EXPIRAION_DATE,ACCT_REISSUE_DATE,ACCT_CURR_CYC_CREDIT,ACCT_CURR_CYC_DEBIT,ACCT_ADDR_ZIP,ACCT_GROUP_ID FROM ACCTDAT " +
		"WHERE ACCT_ID > ? ORDER BY ACCT_ID LIMIT ?";

	private final javax.sql.DataSource dataSource;

	public AccountJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	public void save(Account value)
	{
		Objects.requireNonNull(value, "value");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement =
				connection.prepareStatement(UPSERT))
			{
				int parameter = 1;
				writeLong(statement, parameter++, value.accountId());
				writeString(statement, parameter++, value.activeStatus());
				writeDecimal(statement, parameter++, value.currentBalance());
				writeDecimal(statement, parameter++, value.creditLimit());
				writeDecimal(statement, parameter++, value.cashCreditLimit());
				writeDate(statement, parameter++, value.openDate());
				writeDate(statement, parameter++, value.expirationDate());
				writeDate(statement, parameter++, value.reissueDate());
				writeDecimal(statement, parameter++, value.currentCycleCredit());
				writeDecimal(statement, parameter++, value.currentCycleDebit());
				writeString(statement, parameter++, value.postalCode());
				writeString(statement, parameter++, value.groupId());
				statement.executeUpdate();
				connection.commit();
			}
			catch (SQLException | RuntimeException failure)
			{
				connection.rollback();
				throw failure;
			}
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("record persistence failed", failure);
		}
	}

	public List<Account> findAfter(
		long startExclusive, int limit)
	{
		if (limit < 1) throw new IllegalArgumentException("limit must be positive");
		ArrayList<Account> values =
			new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(SELECT_AFTER))
		{
			writeLong(statement, 1, startExclusive);
			statement.setInt(2, limit);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
					values.add(new Account(
						readLong(rows, "ACCT_ID"),
						readString(rows, "ACCT_ACTIVE_STATUS"),
						readDecimal(rows, "ACCT_CURR_BAL"),
						readDecimal(rows, "ACCT_CREDIT_LIMIT"),
						readDecimal(rows, "ACCT_CASH_CREDIT_LIMIT"),
						readDate(rows, "ACCT_OPEN_DATE"),
						readDate(rows, "ACCT_EXPIRAION_DATE"),
						readDate(rows, "ACCT_REISSUE_DATE"),
						readDecimal(rows, "ACCT_CURR_CYC_CREDIT"),
						readDecimal(rows, "ACCT_CURR_CYC_DEBIT"),
						readString(rows, "ACCT_ADDR_ZIP"),
						readString(rows, "ACCT_GROUP_ID")
					));
			}
			return List.copyOf(values);
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("keyset read failed", failure);
		}
	}

	private static String readString(ResultSet rows, String column)
		throws SQLException { return rows.getString(column); }
	private static long readLong(ResultSet rows, String column)
		throws SQLException { return rows.getLong(column); }
	private static BigDecimal readDecimal(ResultSet rows,
		String column) throws SQLException
		{ return rows.getBigDecimal(column); }
	private static LocalDate readDate(ResultSet rows,
		String column) throws SQLException
	{
		String value = rows.getString(column);
		return value == null || value.isBlank() ? null
			: LocalDate.parse(value.trim());
	}
	private static void writeString(PreparedStatement statement,
		int parameter, String value) throws SQLException
		{ statement.setString(parameter, value); }
	private static void writeLong(PreparedStatement statement,
		int parameter, long value) throws SQLException
		{ statement.setLong(parameter, value); }
	private static void writeDecimal(PreparedStatement statement,
		int parameter, BigDecimal value) throws SQLException
		{ statement.setBigDecimal(parameter, value); }
	private static void writeDate(PreparedStatement statement,
		int parameter, LocalDate value) throws SQLException
		{ statement.setString(parameter, value == null ? null : value.toString()); }
}
