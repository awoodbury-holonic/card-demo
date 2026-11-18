package com.cardemo.transaction;

import java.util.Objects;
import java.util.Optional;

public final class TransactionAddController
{
	private final ValidationState state;
	private final ValidationScreen screen;

	public TransactionAddController(ValidationState state, ValidationScreen screen)
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

	private long cdemoPgmContext;



	public record CardCrossReferenceXrefCardNumSnapshot(
		String xrefCardNum,
		long xrefCustId,
		long xrefAcctId
	) { }

	public static CardCrossReferenceXrefCardNumSnapshot toCardCrossReferenceXrefCardNumSnapshot(
		CardCrossReference record)
	{
		CardCrossReference value = Objects.requireNonNull(record, "record");
		return new CardCrossReferenceXrefCardNumSnapshot(
			value.getXrefCardNum(),
			value.getXrefCustId(),
			value.getXrefAcctId()
		);
	}

	public enum CardCrossReferenceXrefCardNumLookupOutcome { FOUND, NOT_FOUND, FAILED }

	public record CardCrossReferenceXrefCardNumLookupResult(
		CardCrossReferenceXrefCardNumLookupOutcome outcome,
		Optional<CardCrossReferenceXrefCardNumSnapshot> value, String message)
	{
		public CardCrossReferenceXrefCardNumLookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			Objects.requireNonNull(message, "message");
			if ((outcome == CardCrossReferenceXrefCardNumLookupOutcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	public interface CardCrossReferenceXrefCardNumLookupPort
	{
		CardCrossReferenceXrefCardNumLookupResult find(String key);
	}

	public record CardCrossReferenceDetail(
		String xrefCardNum,
		long xrefAcctId
	) { }

	public static CardCrossReferenceDetail toDetail(CardCrossReference record)
	{
		CardCrossReference value = Objects.requireNonNull(record, "record");
		return new CardCrossReferenceDetail(
			value.getXrefCardNum(),
			value.getXrefAcctId()
		);
	}

	public enum DetailLookupOutcome { FOUND, NOT_FOUND, FAILED }

	public record DetailLookupResult(DetailLookupOutcome outcome,
		Optional<CardCrossReferenceDetail> detail, String message)
	{
		public DetailLookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			Objects.requireNonNull(detail, "detail");
			Objects.requireNonNull(message, "message");
			if ((outcome == DetailLookupOutcome.FOUND) != detail.isPresent())
				throw new IllegalArgumentException("detail must match lookup outcome");
		}

		public static DetailLookupResult found(CardCrossReference record)
		{
			return new DetailLookupResult(DetailLookupOutcome.FOUND,
				Optional.of(toDetail(record)), "");
		}

		public static DetailLookupResult notFound()
		{
			return new DetailLookupResult(DetailLookupOutcome.NOT_FOUND,
				Optional.empty(), "Account ID NOT found...");
		}

		public static DetailLookupResult failed()
		{
			return new DetailLookupResult(DetailLookupOutcome.FAILED,
				Optional.empty(), "Unable to lookup Acct in XREF AIX file...");
		}
	}

	public interface DetailLookupPort
	{
		DetailLookupResult find(long key);
	}

	public static DetailLookupResult inquire(long key,
		DetailLookupPort repository)
	{
		return Objects.requireNonNull(repository, "repository").find(key);
	}

	public enum TransferContext { RESET }

	public record TransferContinuation(String fromTransactionId,
		String fromProgram, TransferContext context)
	{
		public TransferContinuation
		{
			Objects.requireNonNull(fromTransactionId, "fromTransactionId");
			Objects.requireNonNull(fromProgram, "fromProgram");
			Objects.requireNonNull(context, "context");
		}
	}

	public record TransferNavigation(String destination,
		TransferContinuation continuation)
	{
		public TransferNavigation
		{
			Objects.requireNonNull(destination, "destination");
			Objects.requireNonNull(continuation, "continuation");
			if (destination.isBlank())
				throw new IllegalArgumentException("destination is required");
		}
	}

	public static TransferNavigation planTransfer(
		Optional<String> normalizedDestination,
		String fromTransactionId, String fromProgram)
	{
		String destination = Objects.requireNonNull(normalizedDestination,
			"normalizedDestination").orElse("COSGN00C");
		return new TransferNavigation(destination,
			new TransferContinuation(fromTransactionId, fromProgram,
				TransferContext.RESET));
	}

}
