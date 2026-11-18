package com.cardemo.batch;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public final class DailyTransactionPostingTimestampFormatter
{
	private DailyTransactionPostingTimestampFormatter() { }
	public static String format(LocalDateTime sourceLocalDateTime)
	{
		String rendered = Objects.requireNonNull(sourceLocalDateTime,
			"sourceLocalDateTime").format(DateTimeFormatter.ofPattern(
				"uuuu'-'MM'-'dd'-'HH'.'mm'.'ss'.'SS'0000'", Locale.ROOT));
		if (rendered.length() != 26)
			throw new IllegalArgumentException("source timestamp width");
		return rendered;
	}
}
