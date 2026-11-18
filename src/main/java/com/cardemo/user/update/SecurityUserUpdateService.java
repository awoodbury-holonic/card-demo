package com.cardemo.user.update;

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
public final class SecurityUserUpdateService
{
	public enum Outcome { UPDATED, NOT_FOUND, FAILED }
	public enum LookupOutcome { FOUND, NOT_FOUND, FAILED }
	public record UserValue(String userId,
		String firstName, String lastName,
		String password, String userType) { }
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
	public record UpdateRequest<T>(T current, T requested,
		List<String> missingFields)
	{
		public UpdateRequest
		{
			Objects.requireNonNull(current, "current");
			Objects.requireNonNull(requested, "requested");
			missingFields = List.copyOf(missingFields);
			if (missingFields.size() > 5)
				throw new IllegalArgumentException(
					"validation evidence exceeds source bound");
		}
	}
	public record Result<T>(Outcome outcome, T value)
	{
		public Result
		{
			Objects.requireNonNull(outcome, "outcome");
			Objects.requireNonNull(value, "value");
		}
	}

	private static final String SELECT_SQL =
		"SELECT SEC_USR_ID, SEC_USR_FNAME, " +
		"SEC_USR_LNAME, SEC_USR_PWD, " +
		"SEC_USR_TYPE FROM USRSEC " +
		"WHERE " + UserIdentityPolicy.keySql("SEC_USR_ID") + " = ?";
	private static final String UPDATE_SQL =
		"UPDATE USRSEC SET SEC_USR_FNAME = ?, " +
		"SEC_USR_LNAME = ?, SEC_USR_PWD = ?, " +
		"SEC_USR_TYPE = ? WHERE " + UserIdentityPolicy.keySql("SEC_USR_ID") + " = ?";

	private final javax.sql.DataSource dataSource;

	public SecurityUserUpdateService(javax.sql.DataSource dataSource)
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
					rows.getString(4), rows.getString(5));
				if (rows.next())
					return new LookupResult<>(LookupOutcome.FAILED,
						Optional.empty());
				return new LookupResult<>(LookupOutcome.FOUND,
					Optional.of(value));
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(SecurityUserUpdateService.class).warn("relational validated update semantic service: find failed", failure);
			return new LookupResult<>(LookupOutcome.FAILED,
				Optional.empty());
		}
	}

	public Result<UserValue> update(
		UpdateRequest<UserValue> request)
	{
		UpdateRequest<UserValue> value =
			Objects.requireNonNull(request, "request");
		if (!value.missingFields().isEmpty())
			return new Result<>(Outcome.FAILED, value.current());
		UserValue requested = value.requested();
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(UPDATE_SQL))
		{
			statement.setString(1, requested.firstName());
			statement.setString(2, requested.lastName());
			statement.setString(3, requested.password());
			statement.setString(4, requested.userType());
			statement.setString(5, requested.userId());
			int changed = statement.executeUpdate();
			if (changed == 1)
				return new Result<>(Outcome.UPDATED, requested);
			return new Result<>(changed == 0 ? Outcome.NOT_FOUND : Outcome.FAILED,
				value.current());
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(SecurityUserUpdateService.class).warn("relational validated update semantic service: update failed", failure);
			return new Result<>(Outcome.FAILED, value.current());
		}
	}
}
