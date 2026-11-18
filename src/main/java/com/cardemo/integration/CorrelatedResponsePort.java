package com.cardemo.integration;

public interface CorrelatedResponsePort<C, R>
{
	void respond(C correlation, R response);

}
