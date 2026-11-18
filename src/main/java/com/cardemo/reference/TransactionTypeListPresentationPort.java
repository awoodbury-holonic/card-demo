package com.cardemo.reference;

import java.util.List;

public interface TransactionTypeListPresentationPort<T>
{
	void present(List<T> placements);
}
