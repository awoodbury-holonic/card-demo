package com.cardemo.card;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.slf4j.LoggerFactory;

@Repository
public class CardUpdateCardJdbcAdapter implements CardUpdateOptimisticUpdateService
{
	private static final String LOCK_SQL =
		"SELECT CARD_CVV_CD, CARD_EMBOSSED_NAME, CARD_EXPIRAION_DATE, "
			+ "CARD_ACTIVE_STATUS FROM CARDDAT "
			+ "WHERE CARD_NUM = ? FOR UPDATE";
	private static final String READ_SQL =
		"SELECT CARD_CVV_CD, CARD_EMBOSSED_NAME, CARD_EXPIRAION_DATE, "
			+ "CARD_ACTIVE_STATUS FROM CARDDAT "
			+ "WHERE CARD_NUM = ?";
	private static final String UPDATE_SQL =
		"UPDATE CARDDAT SET CARD_CVV_CD = ?, "
			+ "CARD_EMBOSSED_NAME = ?, CARD_EXPIRAION_DATE = ?, "
			+ "CARD_ACTIVE_STATUS = ? WHERE CARD_NUM = ?";

	private final javax.sql.DataSource dataSource;

	public CardUpdateCardJdbcAdapter(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	@Override
	public UpdateResult updateIfUnchanged(UpdateRequest request)
	{
		UpdateRequest value = Objects.requireNonNull(request,
			"request");
		String error = value.proposed().validationError();
		if (!value.key().matches("[0-9]{16}"))
			error = "Card number must be a 16-digit number.";
		if (!error.isEmpty()) return new UpdateResult(UpdateOutcome.INVALID_INPUT,
			Optional.empty(), error);
		value = new UpdateRequest(value.key(), value.expected(), value.proposed().normalized());
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			connection.setTransactionIsolation(
				Connection.TRANSACTION_REPEATABLE_READ);
			try
			{
				Optional<CardUpdateSnapshot> locked =
					lock(connection, value.key());
				if (locked.isEmpty())
				{
					connection.rollback();
					return new UpdateResult(UpdateOutcome.LOCK_FAILED,
						Optional.empty());
				}
				CardUpdateSnapshot current = locked.orElseThrow();
				if (!current.equals(value.expected()))
				{
					connection.rollback();
					return new UpdateResult(UpdateOutcome.CONCURRENT_CHANGE,
						Optional.of(current));
				}
				if (write(connection, value.key(), value.proposed()) != 1)
				{
					connection.rollback();
					return new UpdateResult(UpdateOutcome.UPDATE_FAILED,
						Optional.of(current));
				}
				connection.commit();
				return new UpdateResult(UpdateOutcome.UPDATED,
					Optional.of(value.proposed()));
			}
			catch (SQLException | RuntimeException failure)
			{
				try { connection.rollback(); }
				catch (SQLException rollbackFailure)
				{ failure.addSuppressed(rollbackFailure); }
				return new UpdateResult(UpdateOutcome.UPDATE_FAILED,
					Optional.empty());
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(CardUpdateOptimisticUpdateService.class).warn("semantic optimistic update service: updateIfUnchanged failed", failure);
			return new UpdateResult(UpdateOutcome.UPDATE_FAILED,
				Optional.empty());
		}
	}

	@Override
	public Optional<CardUpdateSnapshot> findCurrent(String key)
	{
		try (Connection connection = dataSource.getConnection())
		{
			return read(connection, Objects.requireNonNull(key, "key"),
				READ_SQL);
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(CardUpdateOptimisticUpdateService.class).warn("semantic optimistic update service: findCurrent failed", failure);
			return Optional.empty();
		}
	}

	private static Optional<CardUpdateSnapshot> lock(
		Connection connection, String key) throws SQLException
	{
		return read(connection, key, LOCK_SQL);
	}

	private static Optional<CardUpdateSnapshot> read(
		Connection connection, String key, String sql)
		throws SQLException
	{
		try (PreparedStatement statement =
			connection.prepareStatement(sql))
		{
			statement.setString(1, key);
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next()) return Optional.empty();
				String expiry = required(row, "CARD_EXPIRAION_DATE").trim();
				CardUpdateSnapshot snapshot = new CardUpdateSnapshot(
					row.getLong("CARD_CVV_CD"),
					required(row, "CARD_EMBOSSED_NAME").stripTrailing(),
					part(expiry, 0, 4), part(expiry, 5, 7),
					part(expiry, 8, 10),
					required(row, "CARD_ACTIVE_STATUS").trim());
				if (row.next()) throw new SQLException(
					"card key is not unique");
				return Optional.of(snapshot);
			}
		}
	}

	private static int write(Connection connection, String key,
		CardUpdateSnapshot proposed) throws SQLException
	{
		try (PreparedStatement statement =
			connection.prepareStatement(UPDATE_SQL))
		{
			statement.setLong(1, proposed.cvv());
			statement.setString(2, proposed.embossedName());
			statement.setString(3, proposed.expirationYear() + "-" +
				proposed.expirationMonth() + "-" + proposed.expirationDay());
			statement.setString(4, proposed.activeStatus());
			statement.setString(5, key);
			return statement.executeUpdate();
		}
	}

	private static String required(ResultSet row, String column)
		throws SQLException
	{
		String value = row.getString(column);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}

	private static String part(String value, int begin, int end)
		throws SQLException
	{
		if (value.length() < end)
			throw new SQLException("invalid source date geometry");
		return value.substring(begin, end);
	}
}
