package com.cardemo.authorization;

import java.math.BigDecimal;

public record AuthorizationDetail(
	long authorizationDateKey,
	long authorizationTimeKey,
	String originalDate,
	String originalTime,
	String cardNumber,
	String authorizationType,
	String responseCode,
	String responseReason,
	BigDecimal transactionAmount,
	BigDecimal approvedAmount,
	String merchantId,
	String merchantName,
	String transactionId,
	String matchStatus,
	String fraudStatus
)
{
}
