package com.cardemo.batch;

public interface PendingAuthDetailsLoadPort<T>
{
	enum InsertOutcome
	{
		INSERTED,
		ALREADY_PRESENT,
		FAILED
	}

	InsertOutcome insert(T value);

}
