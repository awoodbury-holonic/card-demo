package com.cardemo.reference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import org.springframework.stereotype.Repository;
import org.slf4j.LoggerFactory;

@Repository
public class TransactionTypeJdbcAdapter
	implements TransactionTypeMutationService.MutationPort, TransactionTypeListBrowseService.BrowsePort
{
	private final javax.sql.DataSource dataSource;

	public TransactionTypeJdbcAdapter(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	@Override
	public TransactionTypeMutationService.UpdateOutcome update(String key, String value)
	{
		return mutate("UPDATE TRANSACTION_TYPE SET TR_DESCRIPTION = ? WHERE TR_TYPE = ?",
			statement ->
			{
				statement.setString(1, value);
				statement.setString(2, key);
			});
	}

	@Override
	public TransactionTypeMutationService.DeleteOutcome delete(String key)
	{
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(
				"DELETE FROM TRANSACTION_TYPE WHERE TR_TYPE = ?"))
			{
				statement.setString(1, key);
				if (statement.executeUpdate() != 1)
				{
					connection.rollback();
					return TransactionTypeMutationService.DeleteOutcome.NOT_FOUND;
				}
				connection.commit();
				return TransactionTypeMutationService.DeleteOutcome.DELETED;
			}
			catch (SQLException failure)
			{
				connection.rollback();
				return "23503".equals(failure.getSQLState())
					? TransactionTypeMutationService.DeleteOutcome.REFERENTIAL_INTEGRITY
					: TransactionTypeMutationService.DeleteOutcome.FAILED;
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(TransactionTypeMutationService.class).warn("relational bounded browse mutation bundle: delete failed", failure);
			return TransactionTypeMutationService.DeleteOutcome.FAILED;
		}
	}

	@Override
	public TransactionTypeListBrowseService.Page browse(TransactionTypeListBrowseService.Query query)
	{
		Objects.requireNonNull(query, "query");
		boolean forward = query.direction() == TransactionTypeListBrowseService.Direction.FORWARD;
		String comparison = forward ? ">=" : "<";
		String ordering = forward ? "ASC" : "DESC";
		String sql = "SELECT TR_TYPE, TR_DESCRIPTION FROM TRANSACTION_TYPE WHERE " +
			"TR_TYPE " + comparison + " ? ORDER BY TR_TYPE " + ordering +
			" FETCH FIRST 8 ROWS ONLY";
		ArrayList<TransactionTypeListBrowseService.Row> values = new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, query.startKey());
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
					values.add(new TransactionTypeListBrowseService.Row(
						rows.getString(1), rows.getString(2).trim()));
			}
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("bounded browse failed", failure);
		}
		boolean hasNext = values.size() > 7;
		if (hasNext)
			values.remove(values.size() - 1);
		// A backward page is read nearest-first, but the source fills its
		// screen from the bottom row up, so the page still shows ascending.
		if (!forward)
			Collections.reverse(values);
		return new TransactionTypeListBrowseService.Page(values, query.direction(), hasNext);
	}

	private TransactionTypeMutationService.UpdateOutcome mutate(String sql, StatementBinder binder)
	{
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				binder.bind(statement);
				if (statement.executeUpdate() != 1)
				{
					connection.rollback();
					return TransactionTypeMutationService.UpdateOutcome.NOT_FOUND;
				}
				connection.commit();
				return TransactionTypeMutationService.UpdateOutcome.UPDATED;
			}
			catch (SQLException failure)
			{
				connection.rollback();
				return "40001".equals(failure.getSQLState())
					? TransactionTypeMutationService.UpdateOutcome.DEADLOCK
					: TransactionTypeMutationService.UpdateOutcome.FAILED;
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(TransactionTypeMutationService.class).warn("relational bounded browse mutation bundle: mutate failed", failure);
			return TransactionTypeMutationService.UpdateOutcome.FAILED;
		}
	}

	@FunctionalInterface
	private interface StatementBinder
	{
		void bind(PreparedStatement statement) throws SQLException;
	}
}
