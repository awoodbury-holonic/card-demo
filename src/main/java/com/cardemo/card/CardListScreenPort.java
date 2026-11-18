package com.cardemo.card;

import java.util.List;
import java.util.Objects;

public interface CardListScreenPort
{
	sealed interface FieldValue permits TextField, NumericField
	{
		int ordinal();
		String name();
		int offset();
		int length();
	}

	record TextField(int ordinal, String name, int offset, int length,
		String value) implements FieldValue
	{
		public TextField
		{
			Objects.requireNonNull(value);
		}
	}

	record NumericField(int ordinal, String name, int offset, int length,
		long value) implements FieldValue { }

	record CardList(List<FieldValue> fields)
	{
		public CardList
		{
			fields = List.copyOf(fields);
			if (fields.size() != 271)
				throw new IllegalArgumentException("screen field count");
			for (int i = 0; i < fields.size(); i++)
			{
				FieldValue field = fields.get(i);
				if (field.ordinal() != i + 1 || field.name() == null ||
					field.name().isBlank() || field.offset() < 0 ||
					field.length() <= 0)
					throw new IllegalArgumentException("screen field layout");
			}
		}
	}

	void send(CardList screen);

}
