package com.cardemo.authorization;

public interface ScheduledResourcePort
{
	enum ScheduleOutcome
	{
		READY,
		FAILED
	}

	ScheduleOutcome ensureReady();

}
