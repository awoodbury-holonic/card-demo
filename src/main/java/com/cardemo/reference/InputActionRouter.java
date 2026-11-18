package com.cardemo.reference;

public interface InputActionRouter<I, A>
{
	A route(I input);

}
