package com.cardemo.transaction;

public interface TransactionAddScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> resetAndPresent();

}
