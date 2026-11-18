package com.cardemo.user.delete;

public interface UserDeleteScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> resetAndPresent();

}
