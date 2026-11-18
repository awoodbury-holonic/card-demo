package com.cardemo.card;

public interface InputActionRouter<I, A>
{
	A route(I input);

}
