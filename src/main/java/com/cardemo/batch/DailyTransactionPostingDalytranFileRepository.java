package com.cardemo.batch;

public interface DailyTransactionPostingDalytranFileRepository
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

	AccessResult<Void> openDalytranFile();
	AccessResult<Void> closeDalytranFile();
	AccessResult<Void> close();
	enum ReadOutcome
	{
		RECORD,
		END_OF_DATA,
		FAILED
	}

	record ReadResult<T>(T value, ReadOutcome outcome, String status)
	{
		public ReadResult(T value, ReadOutcome outcome)
		{
			this(value, outcome, "");
		}
	}

	ReadResult<DailyTransaction> readNext();

}
