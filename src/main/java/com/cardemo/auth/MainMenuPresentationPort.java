package com.cardemo.auth;

import java.util.List;

public interface MainMenuPresentationPort<T>
{
	void present(List<T> placements);
}
