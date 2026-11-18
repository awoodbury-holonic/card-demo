package com.cardemo.report;

import java.util.Objects;

public interface TransactionReportRequestTdQueuePort
{
	enum WriteOutcome { NORMAL, REJECTED }
	record WriteResult(WriteOutcome outcome, int response, int reason)
	{
		public WriteResult
		{
			Objects.requireNonNull(outcome, "outcome");
		}
	}
	record WriteRequest(String queue, String recordImage)
	{
		public WriteRequest
		{
			Objects.requireNonNull(queue, "queue");
			Objects.requireNonNull(recordImage, "recordImage");
			if (recordImage.length() != 80)
				throw new IllegalArgumentException("record image width");
		}
	}
	WriteResult write(WriteRequest request);
	default WriteRequest request(String recordImage)
	{
		return new WriteRequest("JOBS", recordImage);
	}
}
