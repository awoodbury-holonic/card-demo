package com.cardemo.authorization;

import java.util.Objects;

public interface AuthorizationDecisionMessagingPort
{
	enum AccessOutcome
	{
		COMPLETED,
		END_OF_DATA,
		REJECTED
	}

	record AccessResult<T>(T value, AccessOutcome outcome) { }
	record QueueDefinition(String name)
	{
		public QueueDefinition
		{
			Objects.requireNonNull(name, "name");
			if (name.isBlank() || name.length() > 48)
				throw new IllegalArgumentException("invalid queue name");
		}
	}
	record QueueHandle(String name) { }

	AccessResult<String> receive(QueueHandle queue);
	AccessResult<QueueHandle> openQueue(QueueDefinition queue);
}
