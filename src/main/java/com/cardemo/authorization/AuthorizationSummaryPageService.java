package com.cardemo.authorization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface AuthorizationSummaryPageService<Page, Cursor>
{
	record PageRequest<T>(T cursor) { }
	record PaginationAction<T>(T page) { }

	int PAGE_SIZE = 5;
	enum Direction { FORWARD, BACKWARD }
	record PageQuery<K>(Direction direction, Optional<K> anchor)
	{
		public PageQuery
		{
			Objects.requireNonNull(direction, "direction");
			anchor = Objects.requireNonNull(anchor, "anchor");
		}
	}
	record PageSlice<T, K>(List<T> rows,
		Optional<K> firstKey, Optional<K> lastKey,
		boolean hasPrevious, boolean hasNext)
	{
		public PageSlice
		{
			rows = Objects.requireNonNull(rows, "rows");
			firstKey = Objects.requireNonNull(firstKey, "firstKey");
			lastKey = Objects.requireNonNull(lastKey, "lastKey");
			if (rows.size() > PAGE_SIZE)
			{
				throw new IllegalArgumentException("page exceeds source bound");
			}
			rows = List.copyOf(rows);
		}
	}
	PageSlice<Page, Cursor> browse(PageQuery<Cursor> query);

}
