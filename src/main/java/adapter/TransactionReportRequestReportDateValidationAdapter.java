package com.cardemo.report;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;

@org.springframework.stereotype.Component
public final class TransactionReportRequestReportDateValidationAdapter
	implements TransactionReportRequestController.DateValidationPort
{
	@Override
	public Optional<String> rejection(String date)
	{
		try
		{
			LocalDate.parse(Objects.requireNonNull(date, "date"));
			return Optional.empty();
		}
		catch (DateTimeParseException failure)
		{
			return Optional.of("Enter a valid custom date range.");
		}
	}
}
