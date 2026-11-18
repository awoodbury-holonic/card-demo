package com.cardemo.authorization;

public interface DecisionAggregationPort<S, D>
{
	S apply(S current, D decision);

}
