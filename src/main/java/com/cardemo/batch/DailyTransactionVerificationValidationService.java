package com.cardemo.batch;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DailyTransactionVerificationValidationService
{
	private DailyTransactionVerificationValidationService() { }
	public enum ReferenceValidationOutcome
	{
		VALID,
		FIRST_REFERENCE_NOT_FOUND,
		SECOND_REFERENCE_NOT_FOUND
	}

	public static <Input, FirstKey, FirstValue, SecondKey>
		ReferenceValidationOutcome validateReferences(
			Input input,
			Function<Input, FirstKey> firstKey,
			Function<FirstKey,
				Optional<FirstValue>> firstLookup,
			Function<FirstValue, SecondKey> secondKey,
			Predicate<SecondKey> secondExists)
	{
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(firstKey, "firstKey");
		Objects.requireNonNull(firstLookup, "firstLookup");
		Objects.requireNonNull(secondKey, "secondKey");
		Objects.requireNonNull(secondExists, "secondExists");
		var referenced = firstLookup.apply(firstKey.apply(input));
		if (referenced.isEmpty())
			return ReferenceValidationOutcome.FIRST_REFERENCE_NOT_FOUND;
		return secondExists.test(secondKey.apply(referenced.orElseThrow()))
			? ReferenceValidationOutcome.VALID
			: ReferenceValidationOutcome.SECOND_REFERENCE_NOT_FOUND;
	}
}
