package com.cardemo.batch;

import java.util.Objects;
import java.util.Optional;

public interface DailyTransactionPostingXrefFileRepository
{
	enum AccessOutcome
	{
		COMPLETED,
		REJECTED
	}

	record AccessResult<T>(T value, AccessOutcome outcome, String status)
	{
		public AccessResult(T value, AccessOutcome outcome)
		{
			this(value, outcome, "");
		}
	}

	AccessResult<Void> openXrefFile();
	AccessResult<Void> closeXrefFile();
	record CardCrossReferenceSnapshot(
		String xrefCardNum,
		long xrefCustId,
		long xrefAcctId
	) { }

	static CardCrossReferenceSnapshot toSnapshot(CardCrossReference record)
	{
		CardCrossReference value = Objects.requireNonNull(record, "record");
		return new CardCrossReferenceSnapshot(
			value.getXrefCardNum(),
			value.getXrefCustId(),
			value.getXrefAcctId()
		);
	}

	enum LookupOutcome { FOUND, NOT_FOUND }
	record LookupResult(LookupOutcome outcome,
		Optional<CardCrossReferenceSnapshot> value, String message)
	{
		public LookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			Objects.requireNonNull(message, "message");
			if ((outcome == LookupOutcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	LookupResult findByKey(String key);
	default LookupResult missing()
	{
		return new LookupResult(LookupOutcome.NOT_FOUND,
			Optional.empty(), "INVALID CARD NUMBER FOUND");
	}
	AccessResult<Void> close();
	int KEY_OFFSET = 0;
	int KEY_LENGTH = 16;
	default String keyOf(CardCrossReference record)
	{
		return record.getXrefCardNum();
	}

}
