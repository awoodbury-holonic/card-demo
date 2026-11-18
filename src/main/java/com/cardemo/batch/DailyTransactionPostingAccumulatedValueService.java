package com.cardemo.batch;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public final class DailyTransactionPostingAccumulatedValueService
{
	private DailyTransactionPostingAccumulatedValueService() { }
	public enum PersistenceAction
	{
		CREATE,
		UPDATE
	}

	public record Accumulation(PersistenceAction action,
		BigDecimal balance)
	{
		public Accumulation
		{
			Objects.requireNonNull(action, "action");
			Objects.requireNonNull(balance, "balance");
		}
	}

	public static Accumulation apply(
		Optional<BigDecimal> existingBalance,
		BigDecimal amount)
	{
		Objects.requireNonNull(existingBalance,
			"existingBalance");
		Objects.requireNonNull(amount, "amount");
		PersistenceAction action = existingBalance.isPresent()
			? PersistenceAction.UPDATE : PersistenceAction.CREATE;
		BigDecimal prior = existingBalance.orElse(
			BigDecimal.ZERO);
		return new Accumulation(action, prior.add(amount));
	}
}
