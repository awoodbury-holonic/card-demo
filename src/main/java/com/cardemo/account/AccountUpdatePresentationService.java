package com.cardemo.account;

public interface AccountUpdatePresentationService<ScreenState, ScreenPresentation>
{
	ScreenPresentation configurePresentation(ScreenState state);

}
