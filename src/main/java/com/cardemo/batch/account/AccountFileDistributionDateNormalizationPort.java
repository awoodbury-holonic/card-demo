package com.cardemo.batch.account;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Objects;

public interface AccountFileDistributionDateNormalizationPort
{
String normalize(String date);
	static AccountFileDistributionDateNormalizationPort sourceMapping()
	{
		DateTimeFormatter input =
			DateTimeFormatter.ofPattern("uuuu-MM-dd")
				.withResolverStyle(ResolverStyle.STRICT);
		DateTimeFormatter output =
			DateTimeFormatter.ofPattern("uuuuMMdd");
		return date -> LocalDate.parse(
			Objects.requireNonNull(date, "date"), input).format(output);
	}
}
