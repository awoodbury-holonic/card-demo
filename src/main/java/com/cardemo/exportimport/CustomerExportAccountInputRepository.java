package com.cardemo.exportimport;

public interface CustomerExportAccountInputRepository
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
	int KEY_LENGTH = 11;
	default long keyOf(Account record)
	{
		return record.getAcctId();
	}
	AccessResult<Account> readAccountInput();

}
