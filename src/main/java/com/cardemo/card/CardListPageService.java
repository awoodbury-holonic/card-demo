package com.cardemo.card;

public interface CardListPageService<Page, Cursor>
{
	int PAGE_WINDOW = 7;
	enum Direction { FORWARD, BACKWARD }
	record KeyedPageQuery<T>(T startKey, Direction direction) { }

	Page browse(KeyedPageQuery<Cursor> query);

}
