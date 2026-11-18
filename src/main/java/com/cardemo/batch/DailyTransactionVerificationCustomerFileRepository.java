package com.cardemo.batch;

public interface DailyTransactionVerificationCustomerFileRepository
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

	AccessResult<Void> openCustomerFile();
	AccessResult<Void> closeCustomerFile();
	AccessResult<Void> close();

}
