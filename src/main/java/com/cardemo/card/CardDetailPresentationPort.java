package com.cardemo.card;

import java.util.List;

public interface CardDetailPresentationPort<T>
{
	void present(List<T> placements);
}
