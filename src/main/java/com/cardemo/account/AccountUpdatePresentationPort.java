package com.cardemo.account;

import java.util.List;

public interface AccountUpdatePresentationPort<T>
{
	void present(List<T> placements);
}
