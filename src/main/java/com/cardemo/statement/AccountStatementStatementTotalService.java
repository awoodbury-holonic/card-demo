package com.cardemo.statement;

import java.math.BigDecimal;
import java.util.Objects;

public final class AccountStatementStatementTotalService
{
	private AccountStatementStatementTotalService() { }
	public record AmountTotal(BigDecimal value)
	{
		public AmountTotal
		{
			Objects.requireNonNull(value, "value");
		}
	}

	public static AmountTotal calculate(Iterable<BigDecimal> amounts)
	{
		return new AmountTotal(total(amounts));
	}

	public static BigDecimal total(
		Iterable<BigDecimal> amounts)
	{
		Objects.requireNonNull(amounts, "amounts");
		BigDecimal total = BigDecimal.ZERO;
		for (BigDecimal amount : amounts)
			total = total.add(Objects.requireNonNull(amount, "amount"));
		return total;
	}
}
