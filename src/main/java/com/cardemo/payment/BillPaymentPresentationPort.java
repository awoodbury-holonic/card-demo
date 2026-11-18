package com.cardemo.payment;

import java.util.List;

public interface BillPaymentPresentationPort<T>
{
	void present(List<T> placements);
}
