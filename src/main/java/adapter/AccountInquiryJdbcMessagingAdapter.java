package com.cardemo.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.LoggerFactory;

@org.springframework.stereotype.Repository
public class AccountInquiryJdbcMessagingAdapter implements AccountInquiryMessagingPort
{
	private static final String INSERT =
		"INSERT INTO MESSAGE_QUEUE (QUEUE_NAME, PAYLOAD) VALUES (?, ?)";
	private static final String SELECT_AFTER =
		"SELECT MESSAGE_ID, PAYLOAD FROM MESSAGE_QUEUE " +
		"WHERE QUEUE_NAME = ? AND MESSAGE_ID > ? " +
		"ORDER BY MESSAGE_ID LIMIT 1 FOR UPDATE";
	private static final String DELETE =
		"DELETE FROM MESSAGE_QUEUE WHERE MESSAGE_ID = ?";

	private final javax.sql.DataSource dataSource;
	private final ConcurrentMap<String, Long> anchors =
		new ConcurrentHashMap<>();

	public AccountInquiryJdbcMessagingAdapter(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	@Override
	public AccessResult<QueueHandle> openQueue(QueueDefinition queue)
	{
		QueueDefinition value = Objects.requireNonNull(queue, "queue");
		anchors.putIfAbsent(value.name(), 0L);
		return new AccessResult<>(new QueueHandle(value.name()),
			AccessOutcome.COMPLETED);
	}

	public AccessResult<Void> closeQueue(QueueHandle queue)
	{
		QueueHandle value = Objects.requireNonNull(queue, "queue");
		anchors.remove(value.name());
		return new AccessResult<>(null, AccessOutcome.COMPLETED);
	}

	public AccessResult<Void> send(QueueHandle queue, String message)
	{
		QueueHandle destination = Objects.requireNonNull(queue, "queue");
		String payload = Objects.requireNonNull(message, "message");
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(INSERT))
		{
			statement.setString(1, destination.name());
			statement.setString(2, payload);
			return new AccessResult<>(null, statement.executeUpdate() == 1
				? AccessOutcome.COMPLETED : AccessOutcome.REJECTED);
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(AccountInquiryMessagingPort.class).warn("messaging port: send failed", failure);
			return new AccessResult<>(null, AccessOutcome.REJECTED);
		}
	}

	@Override
	public AccessResult<String> receive(QueueHandle queue)
	{
		QueueHandle source = Objects.requireNonNull(queue, "queue");
		Long start = anchors.get(source.name());
		if (start == null)
			return new AccessResult<>(null, AccessOutcome.REJECTED);
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				try (PreparedStatement select =
					connection.prepareStatement(SELECT_AFTER))
				{
					select.setString(1, source.name());
					select.setLong(2, start);
					try (ResultSet rows = select.executeQuery())
					{
						if (!rows.next())
						{
							connection.rollback();
							return new AccessResult<>(null,
								AccessOutcome.END_OF_DATA);
						}
						long id = rows.getLong("MESSAGE_ID");
						String payload = rows.getString("PAYLOAD");
						try (PreparedStatement delete =
							connection.prepareStatement(DELETE))
						{
							delete.setLong(1, id);
							if (delete.executeUpdate() != 1)
								throw new SQLException(
									"queue row changed concurrently");
						}
						connection.commit();
						anchors.put(source.name(), id);
						return new AccessResult<>(payload,
							AccessOutcome.COMPLETED);
					}
				}
			}
			catch (SQLException | RuntimeException failure)
			{
				LoggerFactory.getLogger(AccountInquiryMessagingPort.class).warn("messaging port: receive failed", failure);
				connection.rollback();
				return new AccessResult<>(null, AccessOutcome.REJECTED);
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(AccountInquiryMessagingPort.class).warn("messaging port: receive failed", failure);
			return new AccessResult<>(null, AccessOutcome.REJECTED);
		}
	}
}
