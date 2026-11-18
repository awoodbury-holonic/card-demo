package com.cardemo.exportimport.importing;

public interface CustomerImportResourceLifecycleRepository
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

	AccessResult<Void> openAll();
	void closeAllIgnoringOutcome();

}
