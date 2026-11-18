package com.cardemo.authorization;

import java.util.Objects;

public final class AuthorizationFraudService
{
	public enum FraudAction
	{
		REPORT,
		REMOVE
	}

	public record FraudCommand(
		String accountId,
		String customerId,
		String cardNum,
		String authTimestamp,
		FraudAction action
	) { }

	public record Result(
		InsertOrUpdateRepository.PersistenceOutcome outcome,
		String diagnostic)
	{
		public Result
		{
			Objects.requireNonNull(outcome, "outcome");
			Objects.requireNonNull(diagnostic, "diagnostic");
		}
	}

	private final InsertOrUpdateRepository<FraudCommand> store;

	public AuthorizationFraudService(InsertOrUpdateRepository<FraudCommand> store)
	{
		this.store = Objects.requireNonNull(store, "store");
	}


	public Result mark(FraudCommand command)
	{
		InsertOrUpdateRepository.PersistenceOutcome outcome =
			store.save(Objects.requireNonNull(command, "command"));
		return new Result(outcome,
			outcome == InsertOrUpdateRepository.PersistenceOutcome.FAILED
				? outcome.name() : "");
	}

}
