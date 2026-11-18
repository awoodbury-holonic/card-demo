package com.cardemo.transaction;

import java.util.List;

public interface TransactionViewPresentationPort<T>
{
	void present(List<T> placements);
}
