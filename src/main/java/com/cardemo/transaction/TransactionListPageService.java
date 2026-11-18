package com.cardemo.transaction;

import java.util.List;

public interface TransactionListPageService
{
	int PAGE_WINDOW = 10;
	enum Direction { FORWARD, BACKWARD }
	enum AnchorMode { INCLUDE_ANCHOR, EXCLUDE_ANCHOR }
	record KeyedPageQuery<T>(T startKey, Direction direction, AnchorMode anchorMode) { }
	record BrowseNavigation<K>(K firstKey, K lastKey, int pageNumber, boolean hasNext) { }

	enum BrowseOutcome { SUCCESS, NOT_FOUND, END_OF_DATA, UNEXPECTED }
	record BrowsePage<T, K>(List<T> rows, BrowseOutcome outcome, K firstKey, K lastKey, boolean hasNext)
	{
		public BrowsePage
		{
			if (rows.size() > PAGE_WINDOW)
			{
				throw new IllegalArgumentException("page exceeds source window");
			}
			rows = List.copyOf(rows);
		}
	}
	BrowsePage<TransactionRow, String> browse(KeyedPageQuery<String> query);

}
