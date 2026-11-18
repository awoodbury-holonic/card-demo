package com.cardemo.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public final class SecurityUserCreateService
{
	public enum Outcome { CREATED, DUPLICATE, FAILED }
	public record UserValue(String userId,
		String firstName, String lastName,
		String password, String userType) { }
	public record CreateRequest<T>(T value,
		List<String> missingFields)
	{
		public CreateRequest
		{
			Objects.requireNonNull(value, "value");
			missingFields = List.copyOf(missingFields);
			if (missingFields.size() > 5)
				throw new IllegalArgumentException(
					"validation evidence exceeds source bound");
		}
	}
	public record Result(Outcome outcome)
	{
		public Result
		{
			Objects.requireNonNull(outcome, "outcome");
		}
	}

	private static final String INSERT_SQL =
		"INSERT INTO USRSEC (SEC_USR_ID, SEC_USR_FNAME, " +
		"SEC_USR_LNAME, SEC_USR_PWD, SEC_USR_TYPE, " +
		"SEC_USR_FILLER) VALUES (?, ?, ?, ?, ?, ?)";

	private final javax.sql.DataSource dataSource;

	public SecurityUserCreateService(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	public Result create(CreateRequest<UserValue> request)
	{
		CreateRequest<UserValue> value =
			Objects.requireNonNull(request, "request");
		if (!value.missingFields().isEmpty())
			return new Result(Outcome.FAILED);
		UserValue record = value.value();
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(INSERT_SQL))
		{
			statement.setString(1, record.userId());
			statement.setString(2, record.firstName());
			statement.setString(3, record.lastName());
			statement.setString(4, record.password());
			statement.setString(5, record.userType());
			statement.setString(6, " ".repeat(23));
			return new Result(statement.executeUpdate() == 1
				? Outcome.CREATED : Outcome.FAILED);
		}
		catch (SQLException failure)
		{
			return new Result("23505".equals(
				failure.getSQLState()) ? Outcome.DUPLICATE : Outcome.FAILED);
		}
	}
}
