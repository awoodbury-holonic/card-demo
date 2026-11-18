package com.cardemo.batch;

public interface DailyTransactionVerificationAccountFileRepository
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

	AccessResult<Void> openAccountFile();
	AccessResult<Void> closeAccountFile();
	AccessResult<Void> close();
	int KEY_OFFSET = 0;
	int KEY_LENGTH = 11;
	default long keyOf(Account record)
	{
		return record.getAcctId();
	}

}
