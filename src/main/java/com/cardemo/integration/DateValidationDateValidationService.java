package com.cardemo.integration;

@org.springframework.stereotype.Service
public final class DateValidationDateValidationService
{
	public record DateValidationResult(int severity, int messageNumber, String message) { }

	private final DateValidationDateValidationAdapter adapter;

	public DateValidationDateValidationService(DateValidationDateValidationAdapter adapter)
	{
		this.adapter = adapter;
	}

	public DateValidationResult validate(String date, String format)
	{
		var validation = adapter.convertDate(date, format);
		return new DateValidationResult(validation.severity(),
			validation.messageNumber(), validation.message());
	}

}
