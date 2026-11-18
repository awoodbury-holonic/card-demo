package com.cardemo.authorization;

public interface RootNavigationPort<T>
{
	enum NavigationOutcome
	{
		ITEM,
		END,
		FAILED
	}

	record NavigationResult<T>(T value, NavigationOutcome outcome) { }

	NavigationResult<T> next();

}
