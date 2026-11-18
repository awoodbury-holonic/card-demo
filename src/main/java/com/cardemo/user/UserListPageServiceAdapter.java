package com.cardemo.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.LoggerFactory;

@org.springframework.stereotype.Repository
public class UserListPageServiceAdapter implements UserListPageService
{
	private final javax.sql.DataSource dataSource;

	public UserListPageServiceAdapter(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource);
	}

	@Override
	public UserListPageService.BrowsePage<UserDto, String> browse(
		UserListPageService.KeyedPageQuery<String> query)
	{
		Objects.requireNonNull(query);
		boolean forward = query.direction() == UserListPageService.Direction.FORWARD;
		String comparator = switch (query.anchorMode())
		{
			case INCLUDE_ANCHOR -> forward ? ">=" : "<=";
			case EXCLUDE_ANCHOR -> forward ? ">" : "<";
		};
		String sql = "SELECT SEC_USR_ID, SEC_USR_FNAME, "
			+ "SEC_USR_LNAME, SEC_USR_TYPE FROM USRSEC WHERE "
			+ "SEC_USR_ID " + comparator + " ? ORDER BY SEC_USR_ID "
			+ (forward ? "ASC" : "DESC");
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, query.startKey());
			statement.setMaxRows(UserListPageService.PAGE_WINDOW + 1);
			try (ResultSet results = statement.executeQuery())
			{
				List<UserDto> rows = new ArrayList<>(
					UserListPageService.PAGE_WINDOW + 1);
				while (results.next())
				{
					rows.add(new UserDto(
						required(results, "SEC_USR_ID").trim(),
						required(results, "SEC_USR_FNAME").stripTrailing(),
						required(results, "SEC_USR_LNAME").stripTrailing(),
						required(results, "SEC_USR_TYPE").trim()));
				}
				boolean hasExtra = rows.size() > UserListPageService.PAGE_WINDOW;
				if (hasExtra)
				{
					rows.remove(rows.size() - 1);
				}
				if (!forward)
				{
					Collections.reverse(rows);
				}
				if (rows.isEmpty())
				{
					return new UserListPageService.BrowsePage<>(List.of(),
						UserListPageService.BrowseOutcome.END_OF_DATA,
						null, null, !forward);
				}
				return new UserListPageService.BrowsePage<>(rows,
					UserListPageService.BrowseOutcome.SUCCESS,
					rows.get(0).userId(),
					rows.get(rows.size() - 1).userId(),
					forward ? hasExtra : true);
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(UserListPageServiceAdapter.class).warn("relational keyed fixed text4 adapter: browse failed", failure);
			return new UserListPageService.BrowsePage<>(List.of(),
				UserListPageService.BrowseOutcome.UNEXPECTED,
				null, null, false);
		}
	}

	private static String required(ResultSet results, String column)
		throws SQLException
	{
		String value = results.getString(column);
		if (value == null)
		{
			throw new SQLException(column + " is null");
		}
		return value;
	}
}
