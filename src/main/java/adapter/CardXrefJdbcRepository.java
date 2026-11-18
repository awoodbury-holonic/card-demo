package com.cardemo.batch.cardxref;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@org.springframework.stereotype.Repository
public class CardXrefJdbcRepository
{
	private static final String UPSERT =
		"MERGE INTO CCXREF (XREF_CARD_NUM,XREF_CUST_ID,XREF_ACCT_ID) " +
		"KEY(XREF_CARD_NUM) VALUES (?,?,?)";
	private static final String SELECT_AFTER =
		"SELECT XREF_CARD_NUM,XREF_CUST_ID,XREF_ACCT_ID FROM CCXREF " +
		"WHERE XREF_CARD_NUM > ? ORDER BY XREF_CARD_NUM LIMIT ?";

	private final javax.sql.DataSource dataSource;

	public CardXrefJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	public void save(CardCrossReference value)
	{
		Objects.requireNonNull(value, "value");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement =
				connection.prepareStatement(UPSERT))
			{
				int parameter = 1;
				writeString(statement, parameter++, value.cardNumber());
				writeLong(statement, parameter++, value.customerId());
				writeLong(statement, parameter++, value.accountId());
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

	public List<CardCrossReference> findAfter(
		String startExclusive, int limit)
	{
		if (limit < 1) throw new IllegalArgumentException("limit must be positive");
		ArrayList<CardCrossReference> values =
			new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(SELECT_AFTER))
		{
			writeString(statement, 1, startExclusive);
			statement.setInt(2, limit);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
					values.add(new CardCrossReference(
						readString(rows, "XREF_CARD_NUM"),
						readLong(rows, "XREF_CUST_ID"),
						readLong(rows, "XREF_ACCT_ID")
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
	private static void writeString(PreparedStatement statement,
		int parameter, String value) throws SQLException
		{ statement.setString(parameter, value); }
	private static void writeLong(PreparedStatement statement,
		int parameter, long value) throws SQLException
		{ statement.setLong(parameter, value); }
}
