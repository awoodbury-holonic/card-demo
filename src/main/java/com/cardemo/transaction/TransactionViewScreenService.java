package com.cardemo.transaction;

public interface TransactionViewScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> resetAndPresent();

}
