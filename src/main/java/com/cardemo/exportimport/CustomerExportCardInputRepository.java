package com.cardemo.exportimport;

public interface CustomerExportCardInputRepository
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
	default String keyOf(Card record)
	{
		return record.getCardNum();
	}
	AccessResult<Card> readCardInput();

}
