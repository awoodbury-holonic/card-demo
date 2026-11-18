package com.cardemo.exportimport;

public interface CustomerExportCustomerInputRepository
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
	int KEY_LENGTH = 9;
	default long keyOf(Customer record)
	{
		return record.getCustId();
	}
	AccessResult<Customer> readCustomerInput();

}
