package com.cardemo.card;

public interface CardUpdateCarddatRepository
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

	record CardChange() { }

	AccessResult<Void> updateCarddat(CardChange record);

}
