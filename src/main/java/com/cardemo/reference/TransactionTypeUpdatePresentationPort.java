package com.cardemo.reference;

import java.util.List;

public interface TransactionTypeUpdatePresentationPort<T>
{
	void present(List<T> placements);
}
