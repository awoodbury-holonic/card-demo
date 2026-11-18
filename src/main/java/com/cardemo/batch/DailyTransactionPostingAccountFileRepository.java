package com.cardemo.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public interface DailyTransactionPostingAccountFileRepository
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

	static AccountSnapshot toSnapshot(Account record)
	{
		Account value = Objects.requireNonNull(record, "record");
		return new AccountSnapshot(
			value.getAcctId(),
			value.getAcctActiveStatus(),
			value.getAcctCurrBal(),
			value.getAcctCreditLimit(),
			value.getAcctCashCreditLimit(),
			value.getAcctOpenDate(),
			value.getAcctExpiraionDate(),
			value.getAcctReissueDate(),
			value.getAcctCurrCycCredit(),
			value.getAcctCurrCycDebit(),
			value.getAcctAddrZip(),
			value.getAcctGroupId()
		);
	}

	enum LookupOutcome { FOUND, NOT_FOUND }
	record LookupResult(LookupOutcome outcome,
		Optional<AccountSnapshot> value, String message)
	{
		public LookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			Objects.requireNonNull(message, "message");
			if ((outcome == LookupOutcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	LookupResult findByKey(long key);
	default LookupResult missing()
	{
		return new LookupResult(LookupOutcome.NOT_FOUND,
			Optional.empty(), "ACCOUNT RECORD NOT FOUND");
	}
	AccessResult<Void> close();
	enum BalanceUpdateOutcome { UPDATED, NOT_FOUND }
	record BalanceUpdateResult(
		AccountSnapshot value,
		BalanceUpdateOutcome outcome, String diagnostic)
	{
		public BalanceUpdateResult
		{
			Objects.requireNonNull(outcome, "outcome");
			diagnostic = diagnostic == null ? "" : diagnostic;
			if ((outcome == BalanceUpdateOutcome.UPDATED) != (value != null))
				throw new IllegalArgumentException("value must match outcome");
		}
	}
	BalanceUpdateResult applySignedBalanceDelta(
		AccountSnapshot current,
		BigDecimal amount);
	int KEY_OFFSET = 0;
	int KEY_LENGTH = 11;
	default long keyOf(Account record)
	{
		return record.getAcctId();
	}
	default long keyOf(AccountFileEntry record)
	{
		return record.getFdAcctId();
	}

}
