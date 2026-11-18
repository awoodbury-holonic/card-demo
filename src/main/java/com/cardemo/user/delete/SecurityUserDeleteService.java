package com.cardemo.user.delete;

import application.UserIdentityPolicy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;

@Service
public final class SecurityUserDeleteService
{
	public enum Outcome { DELETED, NOT_FOUND, FAILED }
	public enum LookupOutcome { FOUND, NOT_FOUND, FAILED }
	public record UserValue(String userId,
		String firstName, String lastName,
		String userType) { }
	public record LookupResult<T>(LookupOutcome outcome,
		Optional<T> value)
	{
		public LookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			if ((outcome == LookupOutcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}
	public record DeleteRequest(String key,
		List<String> missingFields)
	{
		public DeleteRequest
		{
			Objects.requireNonNull(key, "key");
			missingFields = List.copyOf(missingFields);
			if (missingFields.size() > 1)
				throw new IllegalArgumentException(
					"validation evidence exceeds source bound");
		}
	}
	public record Result<T>(Outcome outcome,
		Optional<T> deletedValue)
	{
		public Result
		{
			Objects.requireNonNull(outcome, "outcome");
			deletedValue = Objects.requireNonNull(deletedValue,
				"deletedValue");
		}
	}

	private static final String SELECT_SQL =
		"SELECT SEC_USR_ID, SEC_USR_FNAME, " +
		"SEC_USR_LNAME, SEC_USR_TYPE FROM USRSEC " +
		"WHERE " + UserIdentityPolicy.keySql("SEC_USR_ID") + " = ? FOR UPDATE";
	private static final String DELETE_SQL =
		"DELETE FROM USRSEC WHERE " + UserIdentityPolicy.keySql("SEC_USR_ID") + " = ?";

	private final javax.sql.DataSource dataSource;

	public SecurityUserDeleteService(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	public LookupResult<UserValue> find(String key)
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(SELECT_SQL))
		{
			statement.setString(1, Objects.requireNonNull(key, "key"));
			try (ResultSet rows = statement.executeQuery())
			{
				if (!rows.next())
					return new LookupResult<>(LookupOutcome.NOT_FOUND,
						Optional.empty());
				UserValue value = new UserValue(
					rows.getString(1), rows.getString(2), rows.getString(3),
					rows.getString(4));
				if (rows.next())
					return new LookupResult<>(LookupOutcome.FAILED,
						Optional.empty());
				return new LookupResult<>(LookupOutcome.FOUND,
					Optional.of(value));
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(SecurityUserDeleteService.class).warn("relational validated delete semantic service: find failed", failure);
			return new LookupResult<>(LookupOutcome.FAILED,
				Optional.empty());
		}
	}

	public Result<UserValue> delete(DeleteRequest request)
	{
		DeleteRequest value = Objects.requireNonNull(request, "request");
		if (!value.missingFields().isEmpty())
			return new Result<>(Outcome.FAILED, Optional.empty());
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				UserValue current;
				try (PreparedStatement select =
						connection.prepareStatement(SELECT_SQL))
				{
					select.setString(1, value.key());
					try (ResultSet rows = select.executeQuery())
					{
						if (!rows.next())
						{
							connection.rollback();
							return new Result<>(Outcome.NOT_FOUND,
								Optional.empty());
						}
						current = new UserValue(
							rows.getString(1), rows.getString(2), rows.getString(3),
							rows.getString(4));
						if (rows.next())
						{
							connection.rollback();
							return new Result<>(Outcome.FAILED,
								Optional.empty());
						}
					}
				}
				try (PreparedStatement delete =
						connection.prepareStatement(DELETE_SQL))
				{
					delete.setString(1, value.key());
					if (delete.executeUpdate() != 1)
					{
						connection.rollback();
						return new Result<>(Outcome.FAILED,
							Optional.empty());
					}
				}
				connection.commit();
				return new Result<>(Outcome.DELETED,
					Optional.of(current));
			}
			catch (SQLException failure)
			{
				LoggerFactory.getLogger(SecurityUserDeleteService.class).warn("relational validated delete semantic service: delete failed", failure);
				connection.rollback();
				return new Result<>(Outcome.FAILED, Optional.empty());
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(SecurityUserDeleteService.class).warn("relational validated delete semantic service: delete failed", failure);
			return new Result<>(Outcome.FAILED, Optional.empty());
		}
	}
}
