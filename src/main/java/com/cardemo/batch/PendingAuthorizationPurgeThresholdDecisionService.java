package com.cardemo.batch;

public interface PendingAuthorizationPurgeThresholdDecisionService<D>
{
	enum ThresholdOutcome
	{
		MET,
		NOT_MET
	}

	ThresholdOutcome assess(D recordValue, D currentValue, int threshold);

}
