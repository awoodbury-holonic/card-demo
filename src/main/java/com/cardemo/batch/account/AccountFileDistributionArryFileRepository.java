package com.cardemo.batch.account;

public interface AccountFileDistributionArryFileRepository
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

	AccessResult<Void> openArryFile();
	AccessResult<Void> writeArryFile(AccountArrayEntry record);

}
