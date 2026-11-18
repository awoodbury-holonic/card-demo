package com.cardemo.account;

public interface InputActionRouter<I, A>
{
	A route(I input);

}
