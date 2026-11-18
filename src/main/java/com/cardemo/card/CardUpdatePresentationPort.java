package com.cardemo.card;

import java.util.List;

public interface CardUpdatePresentationPort<T>
{
	void present(List<T> placements);
}
