package com.cardemo.batch;

public interface TransactionDetailReportXrefFileRepository
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

	AccessResult<Void> openXrefFile();
	AccessResult<Void> closeXrefFile();
	AccessResult<Void> close();
	int KEY_OFFSET = 0;
	int KEY_LENGTH = 16;
	default String keyOf(CardCrossReference record)
	{
		return record.getXrefCardNum();
	}
 	AccessResult<CardCrossReference> findByKey(String key);

}
