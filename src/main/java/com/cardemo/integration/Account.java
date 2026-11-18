package com.cardemo.integration;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Account(
	long accountId,
	String activeStatus,
	BigDecimal currentBalance,
	BigDecimal creditLimit,
	BigDecimal cashCreditLimit,
	LocalDate openDate,
	LocalDate expirationDate,
	LocalDate reissueDate,
	BigDecimal currentCycleCredit,
	BigDecimal currentCycleDebit,
	String groupId
)
{
}
