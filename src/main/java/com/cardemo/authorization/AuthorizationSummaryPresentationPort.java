package com.cardemo.authorization;

import java.util.List;

public interface AuthorizationSummaryPresentationPort<T>
{
	void present(List<T> placements);
}
