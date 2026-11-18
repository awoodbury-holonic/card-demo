package com.cardemo.batch;

public interface DailyTransactionVerificationTransactFileRepository
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

	AccessResult<Void> openTransactFile();
	AccessResult<Void> closeTransactFile();
	AccessResult<Void> close();

}
