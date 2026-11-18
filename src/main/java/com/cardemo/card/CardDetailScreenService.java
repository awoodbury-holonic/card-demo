package com.cardemo.card;

public interface CardDetailScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> presentFreshScreen();

}
