package com.cardemo.card;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/cards")
public final class CardDetailController
{
	private final CardNumberPort cards;

	public CardDetailController(CardNumberPort cards)
	{
		this.cards = Objects.requireNonNull(cards, "cards");
	}

	@GetMapping("/{cardNumber}")
	public ResponseEntity<Detail> findCard(
		@PathVariable("cardNumber") String cardNumber)
	{
		Read result = cards.find(
			Objects.requireNonNull(cardNumber, "cardNumber"));
		return switch (result.outcome())
		{
			case FOUND -> ResponseEntity.ok(toDetail(
				result.value().orElseThrow()));
			case NOT_FOUND -> ResponseEntity.notFound().build();
			case FAILED -> ResponseEntity.internalServerError().build();
		};
	}

	public record FilterProfile(int width, String prompt, String invalidFeedback)
	{
		public FilterProfile
		{
			if (width < 1 || width > 32)
				throw new IllegalArgumentException("bounded filter width required");
			Objects.requireNonNull(prompt, "prompt");
			Objects.requireNonNull(invalidFeedback, "invalidFeedback");
		}
	}

	public enum FilterOutcome { REQUIRED, NON_NUMERIC, VALID }

	public record FilterNumericKey(String digits, int width)
	{
		public FilterNumericKey
		{
			Objects.requireNonNull(digits, "digits");
			if (digits.length() != width || !digits.chars().allMatch(ch -> ch >= '0' && ch <= '9')
				|| digits.chars().allMatch(ch -> ch == '0'))
				throw new IllegalArgumentException("numeric filter key invariant");
		}
	}

	public record FilterValidationResult(FilterOutcome outcome,
		Optional<FilterNumericKey> key, String feedback)
	{
		public FilterValidationResult
		{
			Objects.requireNonNull(outcome, "outcome");
			key = Objects.requireNonNull(key, "key");
			Objects.requireNonNull(feedback, "feedback");
			if ((outcome == FilterOutcome.VALID) != key.isPresent())
				throw new IllegalArgumentException("filter key and outcome disagree");
		}
	}

	public static FilterValidationResult validateOptionalNumericFilter(
		Optional<String> normalizedInput, FilterProfile profile)
	{
		String digits = Objects.requireNonNull(normalizedInput,
			"normalizedInput").orElse("");
		FilterProfile source = Objects.requireNonNull(profile, "profile");
		if (digits.isBlank() || (digits.length() == source.width()
			&& digits.chars().allMatch(ch -> ch == '0')))
			return new FilterValidationResult(FilterOutcome.REQUIRED,
				Optional.empty(), source.prompt());
		if (digits.length() != source.width()
			|| !digits.chars().allMatch(ch -> ch >= '0' && ch <= '9'))
			return new FilterValidationResult(FilterOutcome.NON_NUMERIC,
				Optional.empty(), source.invalidFeedback());
		return new FilterValidationResult(FilterOutcome.VALID,
			Optional.of(new FilterNumericKey(digits, source.width())), "");
	}

	public static FilterProfile firstFilterProfile()
	{
		return new FilterProfile(11,
			"Account number not provided", "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
	}

	public static FilterProfile secondFilterProfile()
	{
		return new FilterProfile(16,
			"Card number not provided", "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
	}

	public record Card(
		String number,
		long accountId,
		long cvv,
		String embossedName,
		LocalDate expirationDate,
		String activeStatus
	) { }

	public enum Outcome { FOUND, NOT_FOUND, FAILED }

	public record Read(
		Outcome outcome,
		Optional<Card> value, String message)
	{
		public Read
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			Objects.requireNonNull(message, "message");
			if ((outcome == Outcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	public interface CardNumberPort
	{
		Read find(String key);
	}

	public record Detail(
		String embossedName,
		String activeStatus,
		String expirationMonth,
		String expirationYear
	) { }

	public static Detail toDetail(
		Card record)
	{
		Card value = Objects.requireNonNull(record, "record");
		// A typed date: the source shows its month and year, which it takes from
		// fixed positions of the ISO text. A missing date shows as blanks, as
		// the source's spaces would.
		LocalDate expiration = value.expirationDate();
		return new Detail(
			value.embossedName(),
			value.activeStatus(),
			expiration == null ? " ".repeat(2)
				: String.format(Locale.ROOT, "%02d",
					expiration.getMonthValue()),
			expiration == null ? " ".repeat(4)
				: String.format(Locale.ROOT, "%04d",
					expiration.getYear())
		);
	}
}

@Component
final class CardDetailControllerRelationalRepository
	implements CardDetailController.CardNumberPort
{
	private final javax.sql.DataSource dataSource;

	public CardDetailControllerRelationalRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource);
	}

	@Override
	public CardDetailController.Read find(String key)
	{
		String sql = "SELECT CARD_NUM, CARD_ACCT_ID, "
			+ "CARD_CVV_CD, CARD_EMBOSSED_NAME, CARD_EXPIRAION_DATE, "
			+ "CARD_ACTIVE_STATUS FROM CARDDAT WHERE CARD_NUM = ?";
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, key);
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next()) return result(
					CardDetailController.Outcome.NOT_FOUND,
					Optional.empty(), "The credit card was not found");
				CardDetailController.Card value =
					new CardDetailController.Card(
						required(row, "CARD_NUM").trim(),
						row.getLong("CARD_ACCT_ID"),
						row.getLong("CARD_CVV_CD"),
						required(row, "CARD_EMBOSSED_NAME").stripTrailing(),
						date(row, "CARD_EXPIRAION_DATE"),
						required(row, "CARD_ACTIVE_STATUS").trim());
				return result(CardDetailController.Outcome.FOUND,
					Optional.of(value), "");
			}
		}
		catch (SQLException | NullPointerException failure)
		{
			LoggerFactory.getLogger(CardDetailController.class).warn("relational selected card detail controller: find failed", failure);
			return result(CardDetailController.Outcome.FAILED,
				Optional.empty(), "Credit card lookup failed");
		}
	}

	private static CardDetailController.Read result(
		CardDetailController.Outcome outcome,
		Optional<CardDetailController.Card> value,
		String message)
	{
		return new CardDetailController.Read(outcome, value, message);
	}

	// The project types the source date as LocalDate (#field_java_types).
	// The column holds the ISO text, and a blank one reads as no date.
	private static LocalDate date(ResultSet row, String column)
		throws SQLException
	{
		String value = required(row, column).trim();
		return value.isEmpty() ? null : LocalDate.parse(value);
	}

	private static String required(ResultSet row, String column)
		throws SQLException
	{
		String value = row.getString(column);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}
}
