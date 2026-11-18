package com.cardemo.batch;

public interface ChildNavigationPort<T>
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
