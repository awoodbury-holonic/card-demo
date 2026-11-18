package com.cardemo.user.delete;

import java.util.List;

public interface UserDeletePresentationPort<T>
{
	void present(List<T> placements);
}
