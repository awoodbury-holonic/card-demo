package com.cardemo.card;

import java.util.List;

public interface CardListPresentationPort<T>
{
	void present(List<T> placements);
}
