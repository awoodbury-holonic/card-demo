package com.cardemo.transaction;

import java.util.Objects;
import java.util.Optional;

public final class TransactionAddDateValidationCaller
{
	private final ValidationState state;
	private final ValidationScreen screen;

	public TransactionAddDateValidationCaller(ValidationState state, ValidationScreen screen)
	{
		this.state = state;
		this.screen = screen;
	}

	public record ValidationFailure(String message, String invalidField,
		String continuation)
	{
		public ValidationFailure
		{
			Objects.requireNonNull(message, "message");
			Objects.requireNonNull(invalidField, "invalid field");
			Objects.requireNonNull(continuation, "continuation");
		}
	}
	public record CallResult(String resultImage,
		Optional<ValidationFailure> failure)
	{
		public CallResult
		{
			Objects.requireNonNull(resultImage, "result image");
			failure = Objects.requireNonNull(failure, "failure");
		}
	}
	public interface ValidationState
	{
		void reject(ValidationFailure failure);
	}
	public interface ValidationScreen
	{
		void redisplay(ValidationFailure failure, String resultImage);
	}
	public record DateValidationResult(int severity, int messageNumber,
		String message)
	{
		public DateValidationResult
		{
			Objects.requireNonNull(message, "message");
		}
	}
	public interface ValidationService
	{
		DateValidationResult validate(String date, String picture);
	}
	public interface ResultLayout
	{
		String format(DateValidationResult validation, String date,
			String picture);
	}

	public void apply(CallResult result)
	{
		var value = Objects.requireNonNull(result, "result");
		if (value.failure().isEmpty())
			return;
		var failure = value.failure().orElseThrow();
		state.reject(failure);
		screen.redisplay(failure, value.resultImage());
	}

	public static CallResult validateOriginalDate(
		ValidationService service,
		ResultLayout layout,
		String date, String picture)
	{
		DateValidationResult validation =
			service.validate(date, picture);
		String image = layout.format(validation, date, picture);
		boolean error = validation.severity() != Integer.parseInt("0000")
			&& validation.messageNumber() != Integer.parseInt("2513");
		var failure = error ? Optional.of(new ValidationFailure(
			"Orig Date - Not a valid date...", "ORIGINAL_DATE",
			"SHOW_TRANSACTION_ADD")) : Optional.<ValidationFailure>empty();
		return new CallResult(image, failure);
	}
	public static CallResult validateProcessingDate(
		ValidationService service,
		ResultLayout layout,
		String date, String picture)
	{
		DateValidationResult validation =
			service.validate(date, picture);
		String image = layout.format(validation, date, picture);
		boolean error = validation.severity() != Integer.parseInt("0000")
			&& validation.messageNumber() != Integer.parseInt("2513");
		var failure = error ? Optional.of(new ValidationFailure(
			"Proc Date - Not a valid date...", "PROCESSING_DATE",
			"SHOW_TRANSACTION_ADD")) : Optional.<ValidationFailure>empty();
		return new CallResult(image, failure);
	}

}
