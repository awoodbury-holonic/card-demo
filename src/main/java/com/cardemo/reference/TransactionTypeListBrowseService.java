package com.cardemo.reference;

import java.util.List;
import java.util.Objects;

@org.springframework.stereotype.Service
public final class TransactionTypeListBrowseService
{
	private static final int PAGE_SIZE = 7;

	enum Direction { FORWARD, BACKWARD }

	record Query(String startKey, Direction direction)
	{
		public Query
		{
			Objects.requireNonNull(startKey, "startKey");
			Objects.requireNonNull(direction, "direction");
		}
	}

	record Row(String trType, String trDescription)
	{
		public Row
		{
			Objects.requireNonNull(trType, "trType");
			Objects.requireNonNull(trDescription, "trDescription");
		}
	}

	record Page(List<Row> rows, Direction direction, boolean hasNext)
	{
		public Page
		{
			rows = List.copyOf(rows);
			Objects.requireNonNull(direction, "direction");
			if (rows.size() > PAGE_SIZE)
			{
				throw new IllegalArgumentException("page exceeds source bound");
			}
		}
	}

	public interface BrowsePort
	{
		Page browse(Query query);
	}

	private final BrowsePort pages;

	public TransactionTypeListBrowseService(BrowsePort pages)
	{
		this.pages = Objects.requireNonNull(pages, "pages");
	}

	public Page browse(Query query)
	{
		Query checkedQuery = Objects.requireNonNull(query, "query");
		Page page = Objects.requireNonNull(
			pages.browse(checkedQuery), "page");
		if (page.direction() != checkedQuery.direction())
		{
			throw new IllegalArgumentException("page direction differs from query");
		}
		return page;
	}

}
