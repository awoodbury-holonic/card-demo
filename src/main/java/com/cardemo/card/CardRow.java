package com.cardemo.card;

import java.util.Objects;

public record CardRow(
	String accountId,
	String cardNumber,
	String activeStatus
)
{
	public CardRow
	{
		Objects.requireNonNull(accountId, "accountId");
		Objects.requireNonNull(cardNumber, "cardNumber");
		Objects.requireNonNull(activeStatus, "activeStatus");
	}
}
