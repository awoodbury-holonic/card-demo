package com.cardemo.exportimport.importing;

public interface CustomerImportExportInputRepository
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

	int KEY_OFFSET = 27;
	int KEY_LENGTH = 4;
	default long keyOf(CustomerExportEntry record)
	{
		return record.getExportSequenceNum();
	}
	AccessResult<CustomerExportEntry> readExportInput();

}
