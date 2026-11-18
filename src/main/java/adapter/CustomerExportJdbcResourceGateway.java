import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.LoggerFactory;

public final class CustomerExportJdbcResourceGateway
{
	public enum Outcome { FOUND, NOT_FOUND, END_OF_DATA, UPDATED, DELETED, FAILED }

	public record StoredRecord(String resource, String key, String image)
	{
		public StoredRecord
		{
			resource = requireText(resource, "resource");
			key = requireText(key, "key");
			image = Objects.requireNonNull(image, "image");
		}
	}

	public record Result(Outcome outcome,
		Optional<StoredRecord> record, String detail)
	{
		public Result
		{
			Objects.requireNonNull(outcome, "outcome");
			record = Objects.requireNonNull(record, "record");
			detail = detail == null ? "" : detail;
		}
	}

	private static final String SOURCE_RESOURCES = "ACCOUNT-INPUT,CARD-INPUT,CUSTOMER-INPUT,EXPORT-OUTPUT,TRANSACTION-INPUT,XREF-INPUT";
	private static final String SOURCE_OPERATIONS = "close,open,read,write";
	private final javax.sql.DataSource dataSource;

	public CustomerExportJdbcResourceGateway(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	public Set<String> sourceResources()
	{
		return splitCatalog(SOURCE_RESOURCES);
	}

	public Set<String> sourceOperations()
	{
		return splitCatalog(SOURCE_OPERATIONS);
	}

	public Result find(String resource, String key)
	{
		String resourceName = requireSourceResource(resource);
		String recordKey = requireText(key, "key");
		String sql = "SELECT RECORD_IMAGE FROM PROGRAM_RESOURCE_RECORD "
			+ "WHERE RESOURCE_NAME = ? AND RECORD_KEY = ?";
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, resourceName);
			statement.setString(2, recordKey);
			try (ResultSet rows = statement.executeQuery())
			{
				if (!rows.next()) return result(Outcome.NOT_FOUND, null, "23");
				return result(Outcome.FOUND,
					new StoredRecord(resourceName, recordKey,
						rows.getString("RECORD_IMAGE")), "00");
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(CustomerExportJdbcResourceGateway.class).warn("program resource persistence gateway: find failed", failure);
			return result(Outcome.FAILED, null, sqlDetail(failure));
		}
	}

	public List<StoredRecord> readAfter(String resource,
		String start, int limit)
	{
		String resourceName = requireSourceResource(resource);
		String anchor = start == null ? "" : start;
		if (limit < 1 || limit > 1000)
			throw new IllegalArgumentException("limit must be between 1 and 1000");
		String sql = "SELECT RECORD_KEY, RECORD_IMAGE FROM PROGRAM_RESOURCE_RECORD "
			+ "WHERE RESOURCE_NAME = ? AND RECORD_KEY > ? "
			+ "ORDER BY RECORD_KEY LIMIT ?";
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, resourceName);
			statement.setString(2, anchor);
			statement.setInt(3, limit);
			try (ResultSet rows = statement.executeQuery())
			{
				ArrayList<StoredRecord> values = new ArrayList<>();
				while (rows.next())
					values.add(new StoredRecord(resourceName,
						rows.getString("RECORD_KEY"), rows.getString("RECORD_IMAGE")));
				return List.copyOf(values);
			}
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("keyset read failed: " + sqlDetail(failure), failure);
		}
	}

	public Result upsert(StoredRecord record)
	{
		StoredRecord value = Objects.requireNonNull(record, "record");
		requireSourceResource(value.resource());
		String sql = "MERGE INTO PROGRAM_RESOURCE_RECORD "
			+ "(RESOURCE_NAME, RECORD_KEY, RECORD_IMAGE, UPDATED_AT) "
			+ "KEY (RESOURCE_NAME, RECORD_KEY) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
		return mutate(sql, value, Outcome.UPDATED);
	}

	public Result delete(String resource, String key)
	{
		StoredRecord value = new StoredRecord(requireSourceResource(resource),
			requireText(key, "key"), "");
		String sql = "DELETE FROM PROGRAM_RESOURCE_RECORD "
			+ "WHERE RESOURCE_NAME = ? AND RECORD_KEY = ?";
		return mutate(sql, value, Outcome.DELETED);
	}

	private Result mutate(String sql, StoredRecord value, Outcome success)
	{
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				statement.setString(1, value.resource());
				statement.setString(2, value.key());
				if (success == Outcome.UPDATED) statement.setString(3, value.image());
				int changed = statement.executeUpdate();
				if (changed == 1)
				{
					connection.commit();
					return result(success, success == Outcome.UPDATED ? value : null, "00");
				}
				connection.rollback();
				return result(Outcome.NOT_FOUND, null, "23");
			}
			catch (SQLException | RuntimeException failure)
			{
				try { connection.rollback(); }
				catch (SQLException rollbackFailure)
				{ failure.addSuppressed(rollbackFailure); }
				throw failure;
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(CustomerExportJdbcResourceGateway.class).warn("program resource persistence gateway: mutate failed", failure);
			return result(Outcome.FAILED, null, sqlDetail(failure));
		}
	}

	private String requireSourceResource(String resource)
	{
		String value = requireText(resource, "resource");
		if (!sourceResources().contains(value))
			throw new IllegalArgumentException("resource is not source-declared: " + value);
		return value;
	}

	private static Result result(Outcome outcome, StoredRecord value, String detail)
	{
		return new Result(outcome, Optional.ofNullable(value), detail);
	}

	private static Set<String> splitCatalog(String catalog)
	{
		if (catalog.isBlank()) return Set.of();
		return Set.copyOf(Arrays.asList(catalog.split(",", -1)));
	}

	private static String requireText(String value, String label)
	{
		if (value == null || value.isBlank())
			throw new IllegalArgumentException(label + " is required");
		return value;
	}

	private static String sqlDetail(SQLException failure)
	{
		return String.valueOf(failure.getSQLState()) + ": " + failure.getMessage();
	}
}
