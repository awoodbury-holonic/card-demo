package com.cardemo.account;

public interface AccountViewScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> presentFreshScreen();

}
