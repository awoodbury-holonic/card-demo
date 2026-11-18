package com.cardemo.batch.interest;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface InterestCalculationAccountFileRepository
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

	AccessResult<Void> openAccountFile();
	AccessResult<Void> closeAccountFile();
	AccessResult<Void> update(AccountSnapshot value);
	record AccountSnapshot(
		long acctId,
		String acctActiveStatus,
		BigDecimal acctCurrBal,
		BigDecimal acctCreditLimit,
		BigDecimal acctCashCreditLimit,
		LocalDate acctOpenDate,
		LocalDate acctExpiraionDate,
		LocalDate acctReissueDate,
		BigDecimal acctCurrCycCredit,
		BigDecimal acctCurrCycDebit,
		String acctAddrZip,
		String acctGroupId
	) { }
	AccessResult<Void> close();
	int KEY_OFFSET = 0;
	int KEY_LENGTH = 11;
	default long keyOf(AccountFileEntry record)
	{
		return record.getFdAcctId();
	}
	default long keyOf(Account record)
	{
		return record.getAcctId();
	}

}
