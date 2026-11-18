package com.cardemo.report;

import java.util.List;
import java.util.Objects;

public interface TransactionReportRequestReportSubmissionService<R>
{
	enum Outcome
	{
		CONFIRMATION_REQUIRED,
		CANCELLED,
		SUBMITTED,
		INVALID_CONFIRMATION,
		FAILED
	}

	record SubmissionRequest<R>(String confirmation,
		List<R> records)
	{
		public SubmissionRequest
		{
			Objects.requireNonNull(confirmation, "confirmation");
			records = List.copyOf(records);
			if (records.size() > 1000)
				throw new IllegalArgumentException("record limit");
		}
	}

	record SubmissionResult(Outcome outcome, int submittedRecords)
	{
		public SubmissionResult
		{
			Objects.requireNonNull(outcome, "outcome");
			if (submittedRecords < 0 || submittedRecords > 1000)
				throw new IllegalArgumentException("submitted record count");
		}
	}

	SubmissionResult submit(SubmissionRequest<R> request);

}
