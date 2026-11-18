package com.cardemo.transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.LoggerFactory;

@org.springframework.stereotype.Repository
public class TransactionListPageServiceAdapter implements TransactionListPageService
{
	private final javax.sql.DataSource dataSource;

	public TransactionListPageServiceAdapter(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource);
	}

	@Override
	public TransactionListPageService.BrowsePage<TransactionRow, String> browse(
		TransactionListPageService.KeyedPageQuery<String> query)
	{
		Objects.requireNonNull(query);
		boolean forward = query.direction() == TransactionListPageService.Direction.FORWARD;
		String comparator = switch (query.anchorMode())
		{
			case INCLUDE_ANCHOR -> forward ? ">=" : "<=";
			case EXCLUDE_ANCHOR -> forward ? ">" : "<";
		};
		String sql = "SELECT TRAN_ID, TRAN_ORIG_TS, "
			+ "TRAN_DESC, TRAN_AMT FROM TRANSACT WHERE "
			+ "TRAN_ID " + comparator + " ? ORDER BY TRAN_ID "
			+ (forward ? "ASC" : "DESC");
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, query.startKey());
			statement.setMaxRows(TransactionListPageService.PAGE_WINDOW + 1);
			try (ResultSet results = statement.executeQuery())
			{
				List<TransactionRow> rows = new ArrayList<>(
					TransactionListPageService.PAGE_WINDOW + 1);
				while (results.next())
				{
					rows.add(map(results));
				}
				boolean hasExtra = rows.size() > TransactionListPageService.PAGE_WINDOW;
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
					return new TransactionListPageService.BrowsePage<>(List.of(),
						TransactionListPageService.BrowseOutcome.END_OF_DATA,
						null, null, !forward);
				}
				return new TransactionListPageService.BrowsePage<>(rows,
					TransactionListPageService.BrowseOutcome.SUCCESS,
					rows.get(0).id(),
					rows.get(rows.size() - 1).id(),
					forward ? hasExtra : true);
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(TransactionListPageServiceAdapter.class).warn("relational keyed browse anchor adapter: browse failed", failure);
			return new TransactionListPageService.BrowsePage<>(List.of(),
				TransactionListPageService.BrowseOutcome.UNEXPECTED,
				null, null, false);
		}
	}

	private static TransactionRow map(ResultSet results)
		throws SQLException
	{
		LocalDateTime timestamp = results.getObject(
			"TRAN_ORIG_TS", LocalDateTime.class);
		if (timestamp == null)
		{
			throw new SQLException("TRAN_ORIG_TS is null");
		}
		BigDecimal amount = results.getBigDecimal("TRAN_AMT");
		if (amount == null)
		{
			throw new SQLException("TRAN_AMT is null");
		}
		return new TransactionRow(
			required(results.getString("TRAN_ID"), "TRAN_ID").trim(),
			projectDate(timestamp),
			required(results.getString("TRAN_DESC"),
				"TRAN_DESC").stripTrailing(),
			amount.toPlainString());
	}

	// The list shows the date part only (MM/dd/yy), as the
	// source's list screen does; the column itself is a real SQL TIMESTAMP.
	private static String projectDate(LocalDateTime timestamp)
	{
		return timestamp.format(DateTimeFormatter.ofPattern(
			"MM/dd/yy", Locale.ROOT));
	}

	private static String required(String value, String column)
		throws SQLException
	{
		if (value == null)
		{
			throw new SQLException(column + " is null");
		}
		return value;
	}
}
