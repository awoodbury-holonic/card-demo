package com.cardemo.authorization;

import java.math.BigDecimal;

public record AuthorizationSummary(
	long accountId,
	long customerId,
	String authorizationStatus,
	String accountStatus,
	BigDecimal creditLimit,
	BigDecimal cashLimit,
	BigDecimal creditBalance,
	BigDecimal cashBalance,
	int approvedAuthorizationCount,
	int declinedAuthorizationCount,
	BigDecimal approvedAuthorizationAmount,
	BigDecimal declinedAuthorizationAmount
)
{
}
