package com.cardemo.authorization;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Repository;

@Repository
public class PendingAuthorizationSummaryJdbcRepository
{
	public enum InsertOutcome { INSERTED, ALREADY_PRESENT }

	private static final String INSERT =
		"INSERT INTO PENDING_AUTH_SUMMARY (PA_ACCT_ID,PA_CUST_ID,PA_AUTH_STATUS,PA_ACCOUNT_STATUS,PA_CREDIT_LIMIT,PA_CASH_LIMIT,PA_CREDIT_BALANCE,PA_CASH_BALANCE,PA_APPROVED_AUTH_CNT,PA_DECLINED_AUTH_CNT,PA_APPROVED_AUTH_AMT,PA_DECLINED_AUTH_AMT) " +
		"VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String SELECT_AFTER =
		"SELECT PA_ACCT_ID,PA_CUST_ID,PA_AUTH_STATUS,PA_ACCOUNT_STATUS,PA_CREDIT_LIMIT,PA_CASH_LIMIT,PA_CREDIT_BALANCE,PA_CASH_BALANCE,PA_APPROVED_AUTH_CNT,PA_DECLINED_AUTH_CNT,PA_APPROVED_AUTH_AMT,PA_DECLINED_AUTH_AMT FROM PENDING_AUTH_SUMMARY " +
		"WHERE PA_ACCT_ID > ? ORDER BY PA_ACCT_ID LIMIT ?";

	private final javax.sql.DataSource dataSource;

	public PendingAuthorizationSummaryJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	public InsertOutcome insert(AuthorizationSummary value)
	{
		Objects.requireNonNull(value, "value");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				insert(connection, value);
				connection.commit();
				return InsertOutcome.INSERTED;
			}
			catch (SQLException failure)
			{
				connection.rollback();
				if ("23505".equals(failure.getSQLState()))
					return InsertOutcome.ALREADY_PRESENT;
				throw new IllegalStateException("root insert failed", failure);
			}
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("root transaction failed", failure);
		}
	}

	void insert(Connection connection, AuthorizationSummary value)
		throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(INSERT))
		{
			int parameter = 1;
			writeLong(statement, parameter++, value.accountId());
			writeLong(statement, parameter++, value.customerId());
			writeString(statement, parameter++, value.authorizationStatus());
			writeString(statement, parameter++, value.accountStatus());
			writeDecimal(statement, parameter++, value.creditLimit());
			writeDecimal(statement, parameter++, value.cashLimit());
			writeDecimal(statement, parameter++, value.creditBalance());
			writeDecimal(statement, parameter++, value.cashBalance());
			writeInt(statement, parameter++, value.approvedAuthorizationCount());
			writeInt(statement, parameter++, value.declinedAuthorizationCount());
			writeDecimal(statement, parameter++, value.approvedAuthorizationAmount());
			writeDecimal(statement, parameter++, value.declinedAuthorizationAmount());
			statement.executeUpdate();
		}
	}

	public List<AuthorizationSummary> findAfter(long startExclusive, int limit)
	{
		if (limit < 1) throw new IllegalArgumentException("limit must be positive");
		ArrayList<AuthorizationSummary> values = new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(SELECT_AFTER))
		{
			statement.setLong(1, startExclusive);
			statement.setInt(2, limit);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
					values.add(new AuthorizationSummary(
						readLong(rows, "PA_ACCT_ID"),
						readLong(rows, "PA_CUST_ID"),
						readString(rows, "PA_AUTH_STATUS"),
						readString(rows, "PA_ACCOUNT_STATUS"),
						readDecimal(rows, "PA_CREDIT_LIMIT"),
						readDecimal(rows, "PA_CASH_LIMIT"),
						readDecimal(rows, "PA_CREDIT_BALANCE"),
						readDecimal(rows, "PA_CASH_BALANCE"),
						readInt(rows, "PA_APPROVED_AUTH_CNT"),
						readInt(rows, "PA_DECLINED_AUTH_CNT"),
						readDecimal(rows, "PA_APPROVED_AUTH_AMT"),
						readDecimal(rows, "PA_DECLINED_AUTH_AMT")
					));
			}
			return List.copyOf(values);
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("root keyset read failed", failure);
		}
	}

	private static String readString(ResultSet rows, String column)
		throws SQLException { return rows.getString(column); }
	private static long readLong(ResultSet rows, String column)
		throws SQLException { return rows.getLong(column); }
	private static int readInt(ResultSet rows, String column)
		throws SQLException { return rows.getInt(column); }
	private static BigDecimal readDecimal(ResultSet rows,
		String column) throws SQLException { return rows.getBigDecimal(column); }
	private static void writeString(PreparedStatement statement,
		int parameter, String value) throws SQLException
		{ statement.setString(parameter, value); }
	private static void writeLong(PreparedStatement statement,
		int parameter, long value) throws SQLException
		{ statement.setLong(parameter, value); }
	private static void writeInt(PreparedStatement statement,
		int parameter, int value) throws SQLException
		{ statement.setInt(parameter, value); }
	private static void writeDecimal(PreparedStatement statement,
		int parameter, BigDecimal value) throws SQLException
		{ statement.setBigDecimal(parameter, value); }
}
