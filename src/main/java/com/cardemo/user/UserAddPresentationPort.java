package com.cardemo.user;

import java.util.List;

public interface UserAddPresentationPort<T>
{
	void present(List<T> placements);
}
