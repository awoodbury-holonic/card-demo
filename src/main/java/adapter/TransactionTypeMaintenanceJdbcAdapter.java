package com.cardemo.reference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import org.springframework.stereotype.Repository;
import org.slf4j.LoggerFactory;

@Repository
public class TransactionTypeMaintenanceJdbcAdapter implements TransactionTypeMaintenanceService.RepositoryPort
{
	private final javax.sql.DataSource dataSource;

	public TransactionTypeMaintenanceJdbcAdapter(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	@Override
	public TransactionTypeMaintenanceService.Result execute(TransactionTypeMaintenanceService.Command command)
	{
		Objects.requireNonNull(command, "command");
		return switch (command.action())
		{
			case READ -> read(command.value().trType());
			case INSERT -> mutate("INSERT INTO TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (?, ?)", command.value(), false);
			case UPDATE -> mutate("UPDATE TRANSACTION_TYPE SET TR_DESCRIPTION = ? WHERE TR_TYPE = ?", command.value(), true);
			case DELETE -> delete(command.value().trType());
		};
	}

	private TransactionTypeMaintenanceService.Result read(String key)
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(
				"SELECT TR_TYPE, TR_DESCRIPTION FROM TRANSACTION_TYPE WHERE TR_TYPE = ?"))
		{
			statement.setString(1, key);
			try (ResultSet rows = statement.executeQuery())
			{
				if (!rows.next())
					return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.NOT_FOUND, null);
				return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.FOUND,
					new TransactionTypeMaintenanceService.Value(rows.getString(1), rows.getString(2).trim()));
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(TransactionTypeMaintenanceService.class).warn("relational two value crud bundle: read failed", failure);
			return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.FAILED, null);
		}
	}

	private TransactionTypeMaintenanceService.Result mutate(String sql, TransactionTypeMaintenanceService.Value value,
		boolean update)
	{
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				if (update)
				{
					statement.setString(1, value.trDescription());
					statement.setString(2, value.trType());
				}
				else
				{
					statement.setString(1, value.trType());
					statement.setString(2, value.trDescription());
				}
				if (statement.executeUpdate() != 1)
				{
					connection.rollback();
					return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.NOT_FOUND, null);
				}
				connection.commit();
				return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.CHANGED, null);
			}
			catch (SQLException failure)
			{
				LoggerFactory.getLogger(TransactionTypeMaintenanceService.class).warn("relational two value crud bundle: mutate failed", failure);
				connection.rollback();
				return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.FAILED, null);
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(TransactionTypeMaintenanceService.class).warn("relational two value crud bundle: mutate failed", failure);
			return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.FAILED, null);
		}
	}

	private TransactionTypeMaintenanceService.Result delete(String key)
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
					return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.NOT_FOUND, null);
				}
				connection.commit();
				return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.CHANGED, null);
			}
			catch (SQLException failure)
			{
				LoggerFactory.getLogger(TransactionTypeMaintenanceService.class).warn("relational two value crud bundle: delete failed", failure);
				connection.rollback();
				return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.FAILED, null);
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(TransactionTypeMaintenanceService.class).warn("relational two value crud bundle: delete failed", failure);
			return new TransactionTypeMaintenanceService.Result(TransactionTypeMaintenanceService.Outcome.FAILED, null);
		}
	}
}
