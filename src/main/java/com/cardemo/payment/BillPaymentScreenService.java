package com.cardemo.payment;

public interface BillPaymentScreenService<Screen>
{
	record FreshScreenResponse<T>(T screen) { }

	FreshScreenResponse<Screen> resetAndPresent();

}
