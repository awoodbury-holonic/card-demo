package com.cardemo.batch;

import java.math.BigDecimal;

public interface DailyTransactionPostingTcatbalFileRepository
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

	AccessResult<Void> openTcatbalFile();
	AccessResult<Void> closeTcatbalFile();
	AccessResult<Void> write(TransactionCategoryBalanceSnapshot value);
	AccessResult<Void> update(TransactionCategoryBalanceSnapshot value);
	record TransactionCategoryBalanceSnapshot(
		long trancatAcctId,
		String trancatTypeCd,
		long trancatCd,
		BigDecimal tranCatBal
	) { }
	AccessResult<Void> close();

}
