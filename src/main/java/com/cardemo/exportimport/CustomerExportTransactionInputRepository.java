package com.cardemo.exportimport;

public interface CustomerExportTransactionInputRepository
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

	int KEY_OFFSET = 0;
	int KEY_LENGTH = 16;
	default String keyOf(Transaction record)
	{
		return record.getTranId();
	}
	AccessResult<Transaction> readTransactionInput();

}
