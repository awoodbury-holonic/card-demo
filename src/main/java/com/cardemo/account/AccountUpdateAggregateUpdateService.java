package com.cardemo.account;

import java.util.Objects;

public interface AccountUpdateAggregateUpdateService
{
	record Aggregate(AccountValue account,
		CustomerValue customer)
	{
		public Aggregate
		{
			Objects.requireNonNull(account, "account");
			Objects.requireNonNull(customer, "customer");
		}
	}

	record UpdateCommand(Aggregate prior, Aggregate requested)
	{
		public UpdateCommand
		{
			Objects.requireNonNull(prior, "prior");
			Objects.requireNonNull(requested, "requested");
		}
	}

	enum Outcome { UPDATED, LOCK_FAILED, STALE, FAILED }
	record Result(Outcome outcome, Aggregate value)
	{
		public Result
		{
			Objects.requireNonNull(outcome, "outcome");
		}
	}

	Result update(UpdateCommand command);
}
