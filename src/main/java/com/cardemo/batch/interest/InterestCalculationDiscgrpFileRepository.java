package com.cardemo.batch.interest;

public interface InterestCalculationDiscgrpFileRepository
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

	AccessResult<Void> openDiscgrpFile();
	AccessResult<Void> closeDiscgrpFile();
	AccessResult<Void> close();

}
