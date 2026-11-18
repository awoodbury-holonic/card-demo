package com.cardemo.integration;

public interface DateValidationDateValidationAdapter
{
	record DateValidationResult(int severity, int messageNumber, String message) { }

	DateValidationResult convertDate(String date, String format);

}
