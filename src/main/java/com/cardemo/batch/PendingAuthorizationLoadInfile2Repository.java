package com.cardemo.batch;

public interface PendingAuthorizationLoadInfile2Repository
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

	AccessResult<PendingAuthorizationDetailInput> readInfile2();

}
