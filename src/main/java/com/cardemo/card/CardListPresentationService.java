package com.cardemo.card;

public interface CardListPresentationService<ScreenState, ScreenPresentation>
{
	ScreenPresentation configurePresentation(ScreenState state);

}
