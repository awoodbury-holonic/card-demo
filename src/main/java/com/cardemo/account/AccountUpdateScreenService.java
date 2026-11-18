package com.cardemo.account;

public interface AccountUpdateScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> presentFreshScreen();

}
