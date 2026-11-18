package com.cardemo.batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@org.springframework.stereotype.Repository
public class CardJdbcRepository
{
	private static final String UPSERT =
		"MERGE INTO CARDDAT (CARD_NUM,CARD_ACCT_ID,CARD_CVV_CD,CARD_EMBOSSED_NAME,CARD_EXPIRAION_DATE,CARD_ACTIVE_STATUS) " +
		"KEY(CARD_NUM) VALUES (?,?,?,?,?,?)";
	private static final String SELECT_AFTER =
		"SELECT CARD_NUM,CARD_ACCT_ID,CARD_CVV_CD,CARD_EMBOSSED_NAME,CARD_EXPIRAION_DATE,CARD_ACTIVE_STATUS FROM CARDDAT " +
		"WHERE CARD_NUM > ? ORDER BY CARD_NUM LIMIT ?";

	private final javax.sql.DataSource dataSource;

	public CardJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	public void save(Card value)
	{
		Objects.requireNonNull(value, "value");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement =
				connection.prepareStatement(UPSERT))
			{
				int parameter = 1;
				writeString(statement, parameter++, value.number());
				writeLong(statement, parameter++, value.accountId());
				writeLong(statement, parameter++, value.cvv());
				writeString(statement, parameter++, value.embossedName());
				writeDate(statement, parameter++, value.expirationDate());
				writeString(statement, parameter++, value.activeStatus());
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

	public List<Card> findAfter(
		String startExclusive, int limit)
	{
		if (limit < 1) throw new IllegalArgumentException("limit must be positive");
		ArrayList<Card> values =
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
					values.add(new Card(
						readString(rows, "CARD_NUM"),
						readLong(rows, "CARD_ACCT_ID"),
						readLong(rows, "CARD_CVV_CD"),
						readString(rows, "CARD_EMBOSSED_NAME"),
						readDate(rows, "CARD_EXPIRAION_DATE"),
						readString(rows, "CARD_ACTIVE_STATUS")
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
	private static void writeDate(PreparedStatement statement,
		int parameter, LocalDate value) throws SQLException
		{ statement.setString(parameter, value == null ? null : value.toString()); }
}
