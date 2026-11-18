package com.cardemo.account;

import java.util.List;

public interface AccountViewPresentationPort<T>
{
	void present(List<T> placements);
}
