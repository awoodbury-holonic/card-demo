package com.cardemo.batch;

public interface PendingAuthSummaryLookupPort<K>
{
	enum LookupOutcome
	{
		FOUND,
		SKIPPED,
		FAILED
	}

	LookupOutcome find(K key);

}
