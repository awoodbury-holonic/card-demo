package com.cardemo.exportimport;

public interface CustomerExportXrefInputRepository
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
	default String keyOf(CardCrossReference record)
	{
		return record.getXrefCardNum();
	}
	AccessResult<CardCrossReference> readXrefInput();

}
