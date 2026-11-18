package com.cardemo.authorization;

public interface PendingAuthorizationSummarySink<T>
{
	void insert(T value);

}
