package com.cardemo.authorization;

import java.util.List;

public interface AuthorizationDetailPresentationPort<T>
{
	void present(List<T> placements);
}
