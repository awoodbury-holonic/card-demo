package com.cardemo.batch;

public interface DailyTransactionVerificationCardFileRepository
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

	AccessResult<Void> openCardFile();
	AccessResult<Void> closeCardFile();
	AccessResult<Void> close();

}
