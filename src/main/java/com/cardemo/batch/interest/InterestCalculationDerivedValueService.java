package com.cardemo.batch.interest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class InterestCalculationDerivedValueService
{
	public BigDecimal derive(BigDecimal firstFactor,
		BigDecimal secondFactor)
	{
		BigDecimal multiplicand = Objects.requireNonNull(
			firstFactor, "firstFactor");
		BigDecimal multiplier = Objects.requireNonNull(
			secondFactor, "secondFactor");
		return multiplicand.multiply(multiplier).divide(
			new BigDecimal("1200"), 2,
			RoundingMode.DOWN);
	}
}
