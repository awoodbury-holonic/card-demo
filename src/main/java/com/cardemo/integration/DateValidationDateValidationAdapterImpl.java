package com.cardemo.integration;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.Locale;

@org.springframework.stereotype.Service
public final class DateValidationDateValidationAdapterImpl implements DateValidationDateValidationAdapter
{
	private enum FeedbackOutcome
	{
		Valid("'0000000000000000'", "Date is valid"),
		InsufficientInput("'000309CB59C3C5C5'", "Insufficient"),
		InvalidValue("'000309CC59C3C5C5'", "Datevalue error"),
		InvalidEra("'000309CD59C3C5C5'", "Invalid Era "),
		UnsupportedRange("'000309D159C3C5C5'", "Unsupp. Range "),
		InvalidMonth("'000309D559C3C5C5'", "Invalid month "),
		BadFormat("'000309D659C3C5C5'", "Bad Pic String "),
		NonNumericInput("'000309D859C3C5C5'", "Nonnumeric data"),
		ZeroEraYear("'000309D959C3C5C5'", "YearInEra is 0 "),
		InvalidDate("", "Date is invalid")
		;

		private final String feedbackCode;
		private final String message;

		FeedbackOutcome(String feedbackCode, String message)
		{
			this.feedbackCode = feedbackCode;
			this.message = message;
		}

		int severity()
		{
			String hex = feedbackCode.replaceAll("[^0-9A-Fa-f]", "");
			return hex.length() < 4 ? 1 : Integer.parseInt(hex.substring(0, 4), 16);
		}

		int messageNumber()
		{
			String hex = feedbackCode.replaceAll("[^0-9A-Fa-f]", "");
			return hex.length() < 8 ? 0 : Integer.parseInt(hex.substring(4, 8), 16);
		}

		String message()
		{
			return message;
		}
	}

	@Override
	public DateValidationDateValidationAdapter.DateValidationResult convertDate(
		String date, String format)
	{
		String value = date == null ? "" : date.trim();
		String picture = format == null ? "" : format.trim().toUpperCase(Locale.ROOT);
		String javaPattern = switch (picture)
		{
		case "YYYY-MM-DD", "CCYY-MM-DD" -> "uuuu-MM-dd";
		case "YYYYMMDD", "CCYYMMDD" -> "uuuuMMdd";
		case "MM/DD/YYYY" -> "MM/dd/uuuu";
		case "MMDDYYYY" -> "MMddyyyy";
		default -> null;
		};
		if (javaPattern == null)
		{
			return result(FeedbackOutcome.BadFormat);
		}
		if (value.length() < javaPattern.length())
		{
			return result(FeedbackOutcome.InsufficientInput);
		}
		if (!value.chars().allMatch(character ->
			Character.isDigit(character) || character == '-' || character == '/'))
		{
			return result(FeedbackOutcome.NonNumericInput);
		}
		try
		{
			DateTimeFormatter formatter =
				new DateTimeFormatterBuilder()
					.parseStrict()
					.appendPattern(javaPattern)
					.toFormatter(Locale.ROOT)
					.withResolverStyle(ResolverStyle.STRICT);
			LocalDate parsed = LocalDate.parse(value, formatter);
			if (parsed.isBefore(LocalDate.parse("1582-10-15")) ||
				parsed.isAfter(LocalDate.parse("9999-12-31")))
			{
				return result(FeedbackOutcome.UnsupportedRange);
			}
			return result(FeedbackOutcome.Valid);
		}
		catch (DateTimeException exception)
		{
			return result(FeedbackOutcome.InvalidValue);
		}
	}

	private static DateValidationDateValidationAdapter.DateValidationResult result(
		FeedbackOutcome outcome)
	{
		return new DateValidationDateValidationAdapter.DateValidationResult(
			outcome.severity(), outcome.messageNumber(), outcome.message());
	}

}
