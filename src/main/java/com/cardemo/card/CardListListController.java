package com.cardemo.card;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public final class CardListListController
{
	public static final int PAGE_WINDOW = 7;

	public enum Direction { FORWARD, BACKWARD }
	public enum AnchorMode { INCLUDE_ANCHOR, EXCLUDE_ANCHOR }

	public record BrowseRequest(String accountFilter, String cardFilter,
		String startKey, Direction direction, AnchorMode anchorMode)
	{
		public BrowseRequest
		{
			accountFilter = optionalDigits(accountFilter, 11,
				"Account filter must contain 11 digits.");
			cardFilter = optionalDigits(cardFilter, 16,
				"Card number must contain 16 digits.");
			startKey = startKey == null ? "" : startKey.trim();
			Objects.requireNonNull(direction, "direction");
			Objects.requireNonNull(anchorMode, "anchorMode");
		}
	}

	public record CardRow(String accountId, String cardNumber,
		String activeStatus)
	{
		public CardRow
		{
			Objects.requireNonNull(accountId, "accountId");
			Objects.requireNonNull(cardNumber, "cardNumber");
			Objects.requireNonNull(activeStatus, "activeStatus");
		}
	}

	public record BrowsePage(List<CardRow> rows, String firstKey,
		String lastKey, boolean hasNext)
	{
		public BrowsePage
		{
			rows = List.copyOf(Objects.requireNonNull(rows));
			if (rows.size() > PAGE_WINDOW)
				throw new IllegalArgumentException("page exceeds source bound");
		}
	}

	public interface BrowseRepository
	{
		BrowsePage browse(BrowseRequest request);
	}

	private final BrowseRepository repository;

	public CardListListController(BrowseRepository repository)
	{
		this.repository = Objects.requireNonNull(repository);
	}

	@GetMapping
	public BrowsePage browse(
		@RequestParam(name = "accountFilter", required = false) String accountFilter,
		@RequestParam(name = "cardFilter", required = false) String cardFilter,
		@RequestParam(name = "startKey", defaultValue = "") String startKey,
		@RequestParam(name = "direction", defaultValue = "FORWARD") Direction direction,
		@RequestParam(name = "anchorMode", defaultValue = "INCLUDE_ANCHOR") AnchorMode anchorMode)
	{
		return repository.browse(new BrowseRequest(accountFilter, cardFilter,
			startKey, direction, anchorMode));
	}

	private static String optionalDigits(String value, int width, String message)
	{
		if (value == null || value.isBlank()) return "";
		String normalized = value.trim();
		if (normalized.length() > width
			|| !normalized.chars().allMatch(ch -> ch >= '0' && ch <= '9'))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		return "0".repeat(width - normalized.length()) + normalized;
	}
}

@Component
final class CardListListControllerJdbcRepository
	implements CardListListController.BrowseRepository
{
	private final javax.sql.DataSource dataSource;

	CardListListControllerJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource);
	}

	@Override
	public CardListListController.BrowsePage browse(CardListListController.BrowseRequest request)
	{
		boolean forward = request.direction() == CardListListController.Direction.FORWARD;
		String comparator = switch (request.anchorMode())
		{
			case INCLUDE_ANCHOR -> forward ? ">=" : "<=";
			case EXCLUDE_ANCHOR -> forward ? ">" : "<";
		};
		StringBuilder sql = new StringBuilder("SELECT CARD_ACCT_ID, "
			+ "CARD_NUM, CARD_ACTIVE_STATUS FROM CARDDAT WHERE "
			+ "CARD_NUM " + comparator + " ?");
		if (!request.accountFilter().isEmpty())
			sql.append(" AND CARD_ACCT_ID = ?");
		if (!request.cardFilter().isEmpty())
			sql.append(" AND CARD_NUM = ?");
		sql.append(" ORDER BY CARD_NUM ")
			.append(forward ? "ASC" : "DESC");
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql.toString()))
		{
			int parameter = 1;
			statement.setString(parameter++, request.startKey());
			if (!request.accountFilter().isEmpty())
				statement.setString(parameter++, request.accountFilter());
			if (!request.cardFilter().isEmpty())
				statement.setString(parameter, request.cardFilter());
			statement.setMaxRows(CardListListController.PAGE_WINDOW + 1);
			try (ResultSet results = statement.executeQuery())
			{
				List<CardListListController.CardRow> rows = new ArrayList<>();
				while (results.next())
					rows.add(new CardListListController.CardRow(
						requiredDigits(results.getString("CARD_ACCT_ID"),
							11),
						requiredDigits(results.getString("CARD_NUM"),
							16),
						required(results.getString("CARD_ACTIVE_STATUS"))));
				boolean hasExtra = rows.size() > CardListListController.PAGE_WINDOW;
				if (hasExtra) rows.remove(rows.size() - 1);
				if (!forward) Collections.reverse(rows);
				String first = rows.isEmpty() ? null : rows.get(0).cardNumber();
				String last = rows.isEmpty() ? null
					: rows.get(rows.size() - 1).cardNumber();
				return new CardListListController.BrowsePage(rows, first, last,
					forward ? hasExtra : true);
			}
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("The card list could not be loaded.", failure);
		}
	}

	private static String required(String value) throws SQLException
	{
		if (value == null) throw new SQLException("null card-list value");
		return value.trim();
	}

	private static String requiredDigits(String value, int width)
		throws SQLException
	{
		String normalized = required(value);
		if (normalized.length() > width
			|| !normalized.chars().allMatch(ch -> ch >= '0' && ch <= '9'))
			throw new SQLException("invalid numeric identifier");
		return "0".repeat(width - normalized.length()) + normalized;
	}
}
