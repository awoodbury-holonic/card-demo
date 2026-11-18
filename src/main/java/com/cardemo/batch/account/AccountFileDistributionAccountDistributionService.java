package com.cardemo.batch.account;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class AccountFileDistributionAccountDistributionService
{
	private final List<Consumer<Account>> outputs;

	public AccountFileDistributionAccountDistributionService(List<Consumer<Account>> outputs)
	{
		this.outputs = List.copyOf(
			Objects.requireNonNull(outputs, "outputs"));
		if (this.outputs.size() != 4)
			throw new IllegalArgumentException("output count differs from intent");
	}

	public void distribute(Account input)
	{
		Objects.requireNonNull(input, "input");
		for (Consumer<Account> output : outputs)
			output.accept(input);
	}
}
