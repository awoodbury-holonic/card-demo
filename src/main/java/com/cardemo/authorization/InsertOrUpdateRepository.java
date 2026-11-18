package com.cardemo.authorization;

public interface InsertOrUpdateRepository<T>
{
	enum PersistenceOutcome
	{
		INSERTED,
		UPDATED,
		FAILED
	}

	PersistenceOutcome save(T value);

}
