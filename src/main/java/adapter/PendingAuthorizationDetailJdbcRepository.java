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
public class PendingAuthorizationDetailJdbcRepository
{
	public enum InsertOutcome { INSERTED, ALREADY_PRESENT }

	private static final String INSERT =
		"INSERT INTO PENDING_AUTH_DETAILS (PA_ACCT_ID, " +
		"PA_AUTH_DATE_9C,PA_AUTH_TIME_9C,PA_AUTH_ORIG_DATE,PA_AUTH_ORIG_TIME,PA_CARD_NUM,PA_AUTH_TYPE,PA_AUTH_RESP_CODE,PA_AUTH_RESP_REASON,PA_TRANSACTION_AMT,PA_APPROVED_AMT,PA_MERCHANT_ID,PA_MERCHANT_NAME,PA_TRANSACTION_ID,PA_MATCH_STATUS,PA_AUTH_FRAUD) VALUES (?, " +
		"?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String SELECT_AFTER =
		"SELECT PA_AUTH_DATE_9C,PA_AUTH_TIME_9C,PA_AUTH_ORIG_DATE,PA_AUTH_ORIG_TIME,PA_CARD_NUM,PA_AUTH_TYPE,PA_AUTH_RESP_CODE,PA_AUTH_RESP_REASON,PA_TRANSACTION_AMT,PA_APPROVED_AMT,PA_MERCHANT_ID,PA_MERCHANT_NAME,PA_TRANSACTION_ID,PA_MATCH_STATUS,PA_AUTH_FRAUD FROM PENDING_AUTH_DETAILS " +
		"WHERE PA_ACCT_ID = ? AND " +
		"(PA_AUTH_DATE_9C, PA_AUTH_TIME_9C) > (?, ?) " +
		"ORDER BY PA_AUTH_DATE_9C, PA_AUTH_TIME_9C LIMIT ?";

	private final javax.sql.DataSource dataSource;

	public PendingAuthorizationDetailJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	public InsertOutcome insert(long parentKey, AuthorizationDetail value)
	{
		Objects.requireNonNull(value, "value");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				insert(connection, parentKey, value);
				connection.commit();
				return InsertOutcome.INSERTED;
			}
			catch (SQLException failure)
			{
				connection.rollback();
				if ("23505".equals(failure.getSQLState()))
					return InsertOutcome.ALREADY_PRESENT;
				throw new IllegalStateException("child insert failed", failure);
			}
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("child transaction failed", failure);
		}
	}

	void insert(Connection connection, long parentKey,
		AuthorizationDetail value) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(INSERT))
		{
			int parameter = 1;
			statement.setLong(parameter++, parentKey);
			writeLong(statement, parameter++, value.authorizationDateKey());
			writeLong(statement, parameter++, value.authorizationTimeKey());
			writeString(statement, parameter++, value.originalDate());
			writeString(statement, parameter++, value.originalTime());
			writeString(statement, parameter++, value.cardNumber());
			writeString(statement, parameter++, value.authorizationType());
			writeString(statement, parameter++, value.responseCode());
			writeString(statement, parameter++, value.responseReason());
			writeDecimal(statement, parameter++, value.transactionAmount());
			writeDecimal(statement, parameter++, value.approvedAmount());
			writeString(statement, parameter++, value.merchantId());
			writeString(statement, parameter++, value.merchantName());
			writeString(statement, parameter++, value.transactionId());
			writeString(statement, parameter++, value.matchStatus());
			writeString(statement, parameter++, value.fraudStatus());
			statement.executeUpdate();
		}
	}

	public List<AuthorizationDetail> findAfter(long parentKey,
		long firstStartExclusive, long secondStartExclusive, int limit)
	{
		if (limit < 1) throw new IllegalArgumentException("limit must be positive");
		ArrayList<AuthorizationDetail> values = new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(SELECT_AFTER))
		{
			statement.setLong(1, parentKey);
			statement.setLong(2, firstStartExclusive);
			statement.setLong(3, secondStartExclusive);
			statement.setInt(4, limit);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
					values.add(new AuthorizationDetail(
						readLong(rows, "PA_AUTH_DATE_9C"),
						readLong(rows, "PA_AUTH_TIME_9C"),
						readString(rows, "PA_AUTH_ORIG_DATE"),
						readString(rows, "PA_AUTH_ORIG_TIME"),
						readString(rows, "PA_CARD_NUM"),
						readString(rows, "PA_AUTH_TYPE"),
						readString(rows, "PA_AUTH_RESP_CODE"),
						readString(rows, "PA_AUTH_RESP_REASON"),
						readDecimal(rows, "PA_TRANSACTION_AMT"),
						readDecimal(rows, "PA_APPROVED_AMT"),
						readString(rows, "PA_MERCHANT_ID"),
						readString(rows, "PA_MERCHANT_NAME"),
						readString(rows, "PA_TRANSACTION_ID"),
						readString(rows, "PA_MATCH_STATUS"),
						readString(rows, "PA_AUTH_FRAUD")
					));
			}
			return List.copyOf(values);
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("child keyset read failed", failure);
		}
	}

	private static String readString(ResultSet rows, String column)
		throws SQLException { return rows.getString(column); }
	private static long readLong(ResultSet rows, String column)
		throws SQLException { return rows.getLong(column); }
	private static BigDecimal readDecimal(ResultSet rows,
		String column) throws SQLException { return rows.getBigDecimal(column); }
	private static void writeString(PreparedStatement statement,
		int parameter, String value) throws SQLException
		{ statement.setString(parameter, value); }
	private static void writeLong(PreparedStatement statement,
		int parameter, long value) throws SQLException
		{ statement.setLong(parameter, value); }
	private static void writeDecimal(PreparedStatement statement,
		int parameter, BigDecimal value) throws SQLException
		{ statement.setBigDecimal(parameter, value); }
}
