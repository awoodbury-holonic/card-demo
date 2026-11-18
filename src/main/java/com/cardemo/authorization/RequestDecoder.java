package com.cardemo.authorization;

public interface RequestDecoder<T>
{
	T decode(String payload);

}
