package com.cardemo.batch;

public interface TransactionDetailReportTrancatgFileRepository
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

	AccessResult<Void> openTrancatgFile();
	AccessResult<Void> closeTrancatgFile();
	AccessResult<Void> close();
 	AccessResult<TransactionCategory> findByKey(String key);

}
