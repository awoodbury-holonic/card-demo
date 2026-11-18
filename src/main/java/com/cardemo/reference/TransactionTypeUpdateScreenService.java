package com.cardemo.reference;

public interface TransactionTypeUpdateScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> presentFreshScreen();

}
