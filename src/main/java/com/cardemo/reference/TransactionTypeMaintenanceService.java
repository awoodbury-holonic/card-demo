package com.cardemo.reference;

import java.util.Objects;

@org.springframework.stereotype.Service
public final class TransactionTypeMaintenanceService
{
	enum Action
	{
		READ,
		INSERT,
		UPDATE,
		DELETE
	}
	enum Outcome { FOUND, CHANGED, NOT_FOUND, FAILED }
	record Value(String trType, String trDescription)
	{
		public Value
		{
			Objects.requireNonNull(trType, "trType");
			Objects.requireNonNull(trDescription, "trDescription");
		}
	}
	record Command(Action action, Value value)
	{
		public Command
		{
			Objects.requireNonNull(action, "action");
			Objects.requireNonNull(value, "value");
		}
	}
	record Result(Outcome outcome, Value value)
	{
		public Result
		{
			Objects.requireNonNull(outcome, "outcome");
			if ((outcome == Outcome.FOUND) != (value != null))
				throw new IllegalArgumentException("only FOUND carries a value");
		}
	}

	public interface RepositoryPort
	{
		Result execute(Command command);
	}

	private final RepositoryPort repository;

	public TransactionTypeMaintenanceService(RepositoryPort repository)
	{
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	public Result execute(Command command)
	{
		return Objects.requireNonNull(
			repository.execute(Objects.requireNonNull(command, "command")),
			"outcome");
	}

}
