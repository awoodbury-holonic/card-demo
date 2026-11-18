package com.cardemo.card;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public final class CardUpdateController
{


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

	public record CardWsCardRidCardnumSnapshot(
		String cardNum,
		long cardAcctId,
		long cardCvvCd,
		String cardEmbossedName,
		LocalDate cardExpiraionDate,
		String cardActiveStatus
	) { }

	public static CardWsCardRidCardnumSnapshot toCardWsCardRidCardnumSnapshot(
		Card record)
	{
		Card value = Objects.requireNonNull(record, "record");
		return new CardWsCardRidCardnumSnapshot(
			value.getCardNum(),
			value.getCardAcctId(),
			value.getCardCvvCd(),
			value.getCardEmbossedName(),
			value.getCardExpiraionDate(),
			value.getCardActiveStatus()
		);
	}

	public enum CardWsCardRidCardnumLookupOutcome { FOUND, NOT_FOUND, FAILED }

	public record CardWsCardRidCardnumLookupResult(
		CardWsCardRidCardnumLookupOutcome outcome,
		Optional<CardWsCardRidCardnumSnapshot> value, String message)
	{
		public CardWsCardRidCardnumLookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			Objects.requireNonNull(message, "message");
			if ((outcome == CardWsCardRidCardnumLookupOutcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	public interface CardWsCardRidCardnumLookupPort
	{
		CardWsCardRidCardnumLookupResult find(String key);
	}

	public void handle()
	{
		throw new UnsupportedOperationException(
			"generated controller requires further Style refinement");
	}
}
