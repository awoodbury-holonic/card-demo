package com.cardemo.report;

import java.util.Locale;
import java.util.Objects;

@org.springframework.stereotype.Service
public final class TransactionReportRequestReportSubmissionAdapter
	implements TransactionReportRequestReportSubmissionService<String>
{
	private final TransactionReportRequestTdQueuePort queue;

	public TransactionReportRequestReportSubmissionAdapter(
		TransactionReportRequestTdQueuePort queue)
	{
		this.queue = Objects.requireNonNull(queue, "queue");
	}

	@Override
	public SubmissionResult submit(SubmissionRequest<String> request)
	{
		SubmissionRequest<String> value =
			Objects.requireNonNull(request, "request");
		String confirmation = value.confirmation().trim()
			.toUpperCase(Locale.ROOT);
		if (confirmation.isEmpty())
			return new SubmissionResult(Outcome.CONFIRMATION_REQUIRED, 0);
		if (confirmation.equals("N"))
			return new SubmissionResult(Outcome.CANCELLED, 0);
		if (!confirmation.equals("Y"))
			return new SubmissionResult(Outcome.INVALID_CONFIRMATION, 0);
		int submitted = 0;
		for (String record : value.records())
		{
			String image = Objects.requireNonNull(record, "record");
			if (image.length() != 80)
				return new SubmissionResult(Outcome.FAILED, submitted);
			var result = queue.write(queue.request(image));
			if (result.outcome() != TransactionReportRequestTdQueuePort.WriteOutcome.NORMAL)
				return new SubmissionResult(Outcome.FAILED, submitted);
			++submitted;
		}
		return new SubmissionResult(Outcome.SUBMITTED, submitted);
	}
}
