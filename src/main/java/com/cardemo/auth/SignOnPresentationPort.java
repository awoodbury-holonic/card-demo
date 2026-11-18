package com.cardemo.auth;

import java.util.List;

public interface SignOnPresentationPort<T>
{
	void present(List<T> placements);
}
