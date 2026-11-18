package com.cardemo.card;

public interface CardUpdateScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> presentFreshScreen();

}
