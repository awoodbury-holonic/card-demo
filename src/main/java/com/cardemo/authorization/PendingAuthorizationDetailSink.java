package com.cardemo.authorization;

public interface PendingAuthorizationDetailSink<T>
{
	void insert(T value);

}
