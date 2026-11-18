package com.cardemo.authorization;

public interface CorrelatedResponsePort<C, R>
{
	void respond(C correlation, R response);

}
