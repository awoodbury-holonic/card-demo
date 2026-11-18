package com.cardemo.batch;

public interface TransactionDetailReportTrantypeFileRepository
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

	AccessResult<Void> openTrantypeFile();
	AccessResult<Void> closeTrantypeFile();
	AccessResult<Void> close();
	int KEY_OFFSET = 0;
	int KEY_LENGTH = 2;
	default String keyOf(TransactionType record)
	{
		return record.getTranType();
	}
 	AccessResult<TransactionType> findByKey(String key);

}
