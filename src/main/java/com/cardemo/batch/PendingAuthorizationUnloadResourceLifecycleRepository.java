package com.cardemo.batch;

public interface PendingAuthorizationUnloadResourceLifecycleRepository
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

	AccessResult<Void> closeAll();
	AccessResult<Void> openAll();
	void closeAllIgnoringOutcome();

}
