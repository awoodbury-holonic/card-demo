package com.cardemo.user;

import java.util.List;

public interface AdminMenuPresentationPort<T>
{
	void present(List<T> placements);
}
