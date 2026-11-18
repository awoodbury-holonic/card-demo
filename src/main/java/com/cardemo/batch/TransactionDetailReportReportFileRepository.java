package com.cardemo.batch;

public interface TransactionDetailReportReportFileRepository
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

	AccessResult<Void> openReportFile();
	AccessResult<Void> closeReportFile();
	AccessResult<Void> close();
	AccessResult<Void> write(String record);

}
