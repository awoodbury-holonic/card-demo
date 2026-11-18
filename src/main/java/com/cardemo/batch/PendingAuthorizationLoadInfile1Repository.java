package com.cardemo.batch;

public interface PendingAuthorizationLoadInfile1Repository
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

	AccessResult<PendingAuthorizationSummaryInput> readInfile1();

}
