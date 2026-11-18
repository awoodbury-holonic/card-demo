package com.cardemo.batch.account;

public interface AccountFileDistributionOutFileRepository
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

	AccessResult<Void> openOutFile();
	AccessResult<Void> writeOutFile(AccountDistributionEntry record);

}
