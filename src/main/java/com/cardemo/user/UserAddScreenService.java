package com.cardemo.user;

public interface UserAddScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> resetAndPresent();

}
