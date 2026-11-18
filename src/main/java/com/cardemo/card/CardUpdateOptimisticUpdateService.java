package com.cardemo.card;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public interface CardUpdateOptimisticUpdateService
{
	enum UpdateOutcome
	{
		UPDATED,
		LOCK_FAILED,
		CONCURRENT_CHANGE,
		UPDATE_FAILED,
		INVALID_INPUT
	}

	record CardUpdateSnapshot(
		long cvv,
		String embossedName,
		String expirationYear,
		String expirationMonth,
		String expirationDay,
		String activeStatus
	)
	{
		public String validationError()
		{
			if (expirationYear == null || !expirationYear.matches("[0-9]{4}") ||
				Integer.parseInt(expirationYear) < 1950 ||
				Integer.parseInt(expirationYear) > 2099)
				return "Expiration year must be between 1950 and 2099.";
			if (expirationMonth == null || !expirationMonth.matches("[0-9]{1,2}") ||
				Integer.parseInt(expirationMonth) < 1 || Integer.parseInt(expirationMonth) > 12)
				return "Expiration month must be between 1 and 12.";
			if (expirationDay == null || !expirationDay.matches("[0-9]{1,2}") ||
				Integer.parseInt(expirationDay) < 1 || Integer.parseInt(expirationDay) > 31)
				return "Expiration day must be between 1 and 31.";
			try { expirationDate(); }
			catch (DateTimeException invalid)
			{ return "Expiration date must be a valid calendar date."; }
			if (cvv < 0 || cvv > 999) return "CVV must contain up to three digits.";
			if (embossedName == null || embossedName.isBlank() ||
				embossedName.length() > 50 || !embossedName.matches("[A-Za-z ]+"))
				return "Embossed name must contain only letters and spaces, up to 50 characters.";
			if (!"Y".equals(activeStatus) && !"N".equals(activeStatus))
				return "Active status must be Y or N.";
			return "";
		}

		private LocalDate expirationDate()
		{
			return LocalDate.of(Integer.parseInt(expirationYear),
				Integer.parseInt(expirationMonth), Integer.parseInt(expirationDay));
		}

		public CardUpdateSnapshot normalized()
		{
			String date = expirationDate().toString();
			return new CardUpdateSnapshot(cvv, embossedName, date.substring(0, 4),
				date.substring(5, 7), date.substring(8, 10), activeStatus);
		}
	}

	record UpdateRequest(String key, CardUpdateSnapshot expected,
		CardUpdateSnapshot proposed)
	{
		public UpdateRequest
		{
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(expected, "expected");
			Objects.requireNonNull(proposed, "proposed");
		}
	}

	record UpdateResult(UpdateOutcome outcome,
		Optional<CardUpdateSnapshot> current, String message)
	{
		public UpdateResult(UpdateOutcome outcome, Optional<CardUpdateSnapshot> current)
		{
			this(outcome, current, "");
		}

		public UpdateResult
		{
			Objects.requireNonNull(outcome, "outcome");
			current = Objects.requireNonNull(current, "current");
			if (outcome == UpdateOutcome.UPDATED && current.isEmpty())
				throw new IllegalArgumentException(
					"updated result requires the accepted snapshot");
		}
	}

	Optional<CardUpdateSnapshot> findCurrent(String key);
	UpdateResult updateIfUnchanged(UpdateRequest request);
}
