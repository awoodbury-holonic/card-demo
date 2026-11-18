package com.cardemo.batch;

public interface PendingAuthSummaryLoadPort<T>
{
	enum InsertOutcome
	{
		INSERTED,
		ALREADY_PRESENT,
		FAILED
	}

	InsertOutcome insert(T value);

}
