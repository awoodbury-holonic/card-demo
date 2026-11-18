package com.cardemo.statement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.LoggerFactory;

public final class MultiResourceFileGateway<R, K>
{
	enum Action { OPEN, READ_NEXT, READ_BY_KEY, CLOSE }
	record Request<R, K>(R resource, Action action,
		Optional<K> key)
	{
		public Request
		{
			Objects.requireNonNull(resource, "resource");
			Objects.requireNonNull(action, "action");
			key = Objects.requireNonNull(key, "key");
			if ((action == Action.READ_BY_KEY) != key.isPresent())
				throw new IllegalArgumentException("key must match action");
		}
	}
	record Response(String status,
		Optional<String> recordImage)
	{
		public Response
		{
			Objects.requireNonNull(status, "status");
			recordImage = Objects.requireNonNull(recordImage,
				"recordImage");
		}
	}

	public interface ResourcePort<K>
	{
		Response open();
		Response readNext();
		Response readByKey(K key);
		Response close();
	}

	private final Map<R, ResourcePort<K>> resources;

	public MultiResourceFileGateway(Map<R, ResourcePort<K>> resources)
	{
		this.resources = Map.copyOf(
			Objects.requireNonNull(resources, "resources"));
		if (this.resources.size() != 4)
			throw new IllegalArgumentException("resource count differs from intent");
	}

	public Response execute(Request<R, K> request)
	{
		Objects.requireNonNull(request, "request");
		ResourcePort<K> resource = resources.get(request.resource());
		if (resource == null)
			throw new IllegalArgumentException("unknown resource");
		return Objects.requireNonNull(switch (request.action())
		{
			case OPEN -> resource.open();
			case READ_NEXT -> resource.readNext();
			case READ_BY_KEY -> resource.readByKey(request.key().orElseThrow());
			case CLOSE -> resource.close();
		}, "resource response");
	}

	@FunctionalInterface
	public interface KeyBinder<K>
	{
		void bind(PreparedStatement statement, int parameter, K key)
			throws SQLException;
	}

	@FunctionalInterface
	public interface RowMapper<K>
	{
		MappedRow<K> map(ResultSet row) throws SQLException;
	}

	public record MappedRow<K>(K key, String recordImage)
	{
		public MappedRow
		{
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(recordImage, "recordImage");
		}
	}

	public static final class JdbcResourcePort<K> implements ResourcePort<K>
	{
		private final javax.sql.DataSource dataSource;
		private final String selectAfter;
		private final String selectByKey;
		private final KeyBinder<K> keyBinder;
		private final RowMapper<K> rowMapper;
		private final K initialKey;
		private K lastKey;
		private boolean open;

		public JdbcResourcePort(javax.sql.DataSource dataSource,
			String selectAfter, String selectByKey, K initialKey,
			KeyBinder<K> keyBinder, RowMapper<K> rowMapper)
		{
			this.dataSource = Objects.requireNonNull(dataSource,
				"dataSource");
			this.selectAfter = Objects.requireNonNull(selectAfter,
				"selectAfter");
			this.selectByKey = Objects.requireNonNull(selectByKey,
				"selectByKey");
			this.initialKey = Objects.requireNonNull(initialKey,
				"initialKey");
			this.keyBinder = Objects.requireNonNull(keyBinder,
				"keyBinder");
			this.rowMapper = Objects.requireNonNull(rowMapper,
				"rowMapper");
			lastKey = initialKey;
		}

		@Override
		public synchronized Response open()
		{
			if (open) return response("41", null);
			lastKey = initialKey;
			open = true;
			return response("00", null);
		}

		@Override
		public synchronized Response readNext()
		{
			if (!open) return response("47", null);
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement =
					connection.prepareStatement(selectAfter))
			{
				keyBinder.bind(statement, 1, lastKey);
				try (ResultSet rows = statement.executeQuery())
				{
					if (!rows.next()) return response("10", null);
					MappedRow<K> value = rowMapper.map(rows);
					lastKey = value.key();
					return response("00", value.recordImage());
				}
			}
			catch (SQLException failure)
			{
				LoggerFactory.getLogger(MultiResourceFileGateway.class).warn("multi resource file gateway: readNext failed", failure);
				return response("30", null);
			}
		}

		@Override
		public synchronized Response readByKey(K key)
		{
			if (!open) return response("47", null);
			try (Connection connection = dataSource.getConnection();
				PreparedStatement statement =
					connection.prepareStatement(selectByKey))
			{
				keyBinder.bind(statement, 1,
					Objects.requireNonNull(key, "key"));
				try (ResultSet rows = statement.executeQuery())
				{
					if (!rows.next()) return response("23", null);
					MappedRow<K> value = rowMapper.map(rows);
					return response("00", value.recordImage());
				}
			}
			catch (SQLException failure)
			{
				LoggerFactory.getLogger(MultiResourceFileGateway.class).warn("multi resource file gateway: readByKey failed", failure);
				return response("30", null);
			}
		}

		@Override
		public synchronized Response close()
		{
			if (!open) return response("42", null);
			open = false;
			return response("00", null);
		}

		private static Response response(String status, String recordImage)
		{
			return new Response(status, Optional.ofNullable(recordImage));
		}
	}
}
