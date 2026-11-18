package com.cardemo.user;

import java.util.List;

public interface UserListPresentationPort<T>
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
