package com.cardemo.user.update;

import java.util.List;

public interface UserUpdatePresentationPort<T>
{
	void present(List<T> placements);
}
