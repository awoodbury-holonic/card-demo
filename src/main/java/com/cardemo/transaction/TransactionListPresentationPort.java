package com.cardemo.transaction;

import java.util.List;

public interface TransactionListPresentationPort<T>
{
	record SelectedListView<Row>(List<Row> rows)
	{
		public SelectedListView
		{
			rows = List.copyOf(rows);
		}
	}

	void present(List<T> placements);
}
