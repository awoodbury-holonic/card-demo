package com.cardemo.batch.account;

public interface AccountFileDistributionAcctfileFileRepository
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

	AccessResult<Void> openAcctfileFile();
	AccessResult<Void> closeAcctfileFile();
	AccessResult<Void> close();
	int KEY_OFFSET = 0;
	int KEY_LENGTH = 11;
	default long keyOf(Account record)
	{
		return record.getAcctId();
	}
	enum ReadOutcome
	{
		RECORD,
		END_OF_DATA,
		FAILED
	}

	record ReadResult<T>(T value, ReadOutcome outcome, String status)
	{
		public ReadResult(T value, ReadOutcome outcome)
		{
			this(value, outcome, "");
		}
	}

	ReadResult<Account> readNext();

}
