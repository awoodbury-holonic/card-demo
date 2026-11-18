package com.cardemo.report;

import java.util.List;

public interface TransactionReportRequestPresentationPort<T>
{
	void present(List<T> placements);
}
