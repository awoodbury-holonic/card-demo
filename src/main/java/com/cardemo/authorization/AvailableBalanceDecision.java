package com.cardemo.authorization;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public interface AvailableBalanceDecision
{
	enum Outcome { APPROVED, DECLINED }
	record CreditPosition(BigDecimal limit,
		BigDecimal balance)
	{
		public CreditPosition
		{
			Objects.requireNonNull(limit, "limit");
			Objects.requireNonNull(balance, "balance");
		}
	}

	default Outcome decide(BigDecimal requestedValue,
		Optional<CreditPosition> primary,
		Optional<CreditPosition> alternate)
	{
		Objects.requireNonNull(requestedValue, "requestedValue");
		Objects.requireNonNull(primary, "primary");
		Objects.requireNonNull(alternate, "alternate");
		CreditPosition selected = primary.orElseGet(() -> alternate.orElse(null));
		if (selected == null)
			return Outcome.DECLINED;
		BigDecimal available = selected.limit().subtract(
			selected.balance());
		return requestedValue.compareTo(available) > 0
			? Outcome.DECLINED : Outcome.APPROVED;
	}

}
