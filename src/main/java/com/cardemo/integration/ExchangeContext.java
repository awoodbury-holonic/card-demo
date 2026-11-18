package com.cardemo.integration;

public record ExchangeContext(
	String messageId,
	String correlationId
)
{
}
