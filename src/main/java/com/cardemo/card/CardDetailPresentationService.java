package com.cardemo.card;

public interface CardDetailPresentationService<ScreenState, ScreenPresentation>
{
	ScreenPresentation configurePresentation(ScreenState state);

}
