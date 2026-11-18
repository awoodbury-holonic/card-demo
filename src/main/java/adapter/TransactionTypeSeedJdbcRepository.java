package com.cardemo.batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@org.springframework.stereotype.Repository
public class TransactionTypeSeedJdbcRepository
{
	private static final String UPSERT =
		"MERGE INTO TRANSACTION_TYPE (TR_TYPE,TR_DESCRIPTION) " +
		"KEY(TR_TYPE) VALUES (?,?)";
	private static final String SELECT_AFTER =
		"SELECT TR_TYPE,TR_DESCRIPTION FROM TRANSACTION_TYPE " +
		"WHERE TR_TYPE > ? ORDER BY TR_TYPE LIMIT ?";

	private final javax.sql.DataSource dataSource;

	public TransactionTypeSeedJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	public void save(TransactionType value)
	{
		Objects.requireNonNull(value, "value");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement =
				connection.prepareStatement(UPSERT))
			{
				int parameter = 1;
				writeString(statement, parameter++, value.code());
				writeString(statement, parameter++, value.description());
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

	public List<TransactionType> findAfter(
		String startExclusive, int limit)
	{
		if (limit < 1) throw new IllegalArgumentException("limit must be positive");
		ArrayList<TransactionType> values =
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
					values.add(new TransactionType(
						readString(rows, "TR_TYPE"),
						readString(rows, "TR_DESCRIPTION")
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
	private static void writeString(PreparedStatement statement,
		int parameter, String value) throws SQLException
		{ statement.setString(parameter, value); }
}
