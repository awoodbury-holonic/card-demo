package com.cardemo.batch.interest;

import java.math.BigDecimal;

public interface InterestCalculationTransactFileRepository
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

	AccessResult<Void> openTransactFile();
	AccessResult<Void> closeTransactFile();
	AccessResult<Void> write(TransactionSnapshot value);
	record TransactionSnapshot(
		String tranId,
		String tranTypeCd,
		long tranCatCd,
		String tranSource,
		String tranDesc,
		BigDecimal tranAmt,
		long tranMerchantId,
		String tranMerchantName,
		String tranMerchantCity,
		String tranMerchantZip,
		String tranCardNum,
		String tranOrigTs,
		String tranProcTs
	) { }
	AccessResult<Void> close();

}
