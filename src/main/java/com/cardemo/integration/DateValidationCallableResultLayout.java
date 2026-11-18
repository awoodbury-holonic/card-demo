package com.cardemo.integration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@org.springframework.stereotype.Service
public final class DateValidationCallableResultLayout
{
	private static final int DECLARED_WIDTH = 80;

	private static void put(StringBuilder result, Map<String, String> values,
		String key, int offset, int length, String defaultValue)
	{
		boolean supplied = values.containsKey(key);
		String value = supplied ? values.get(key) : defaultValue;
		if (!supplied && ("SPACE".equals(value) || "SPACES".equals(value)))
			value = "";
		if (value == null)
			value = "";
		if (value.length() > length)
			value = value.substring(0, length);
		if (value.length() < length)
			value = value + " ".repeat(length - value.length());
		result.replace(offset, offset + length, value);
	}
	public String format(DateValidationDateValidationService.DateValidationResult validation,
		String date, String picture)
	{
		Map<String, String> values = new HashMap<>();
		values.put("u0#2206#working_storage/WS-MESSAGE.WS-SEVERITY#520",
			String.format(Locale.ROOT, "%04d", validation.severity()));
		values.put("u0#2349#working_storage/WS-MESSAGE.WS-MSG-NO#535",
			String.format(Locale.ROOT, "%04d", validation.messageNumber()));
		values.put("u0#2482#working_storage/WS-MESSAGE.WS-RESULT#540", validation.message());
		values.put("u0#2613#working_storage/WS-MESSAGE.WS-DATE#565", date == null ? "" : date);
		values.put("u0#2759#working_storage/WS-MESSAGE.WS-DATE-FMT#586", picture == null ? "" : picture);
		return format(values);
	}

	public String format(Map<String, String> values)
	{
		StringBuilder result = new StringBuilder(" ".repeat(DECLARED_WIDTH));
		put(result, values, "u0#2206#working_storage/WS-MESSAGE.WS-SEVERITY#520", 0,
			4, "");
		put(result, values, "u0#2296#working_storage/WS-MESSAGE.FILLER#524", 4,
			11, "Mesg Code:");
		put(result, values, "u0#2349#working_storage/WS-MESSAGE.WS-MSG-NO#535", 15,
			4, "");
		put(result, values, "u0#2436#working_storage/WS-MESSAGE.FILLER#539", 19,
			1, "SPACE");
		put(result, values, "u0#2482#working_storage/WS-MESSAGE.WS-RESULT#540", 20,
			15, "");
		put(result, values, "u0#2516#working_storage/WS-MESSAGE.FILLER#555", 35,
			1, "SPACE");
		put(result, values, "u0#2562#working_storage/WS-MESSAGE.FILLER#556", 36,
			9, "TstDate:");
		put(result, values, "u0#2613#working_storage/WS-MESSAGE.WS-DATE#565", 45,
			10, "SPACES");
		put(result, values, "u0#2660#working_storage/WS-MESSAGE.FILLER#575", 55,
			1, "SPACE");
		put(result, values, "u0#2706#working_storage/WS-MESSAGE.FILLER#576", 56,
			10, "Mask used:");
		put(result, values, "u0#2759#working_storage/WS-MESSAGE.WS-DATE-FMT#586", 66,
			10, "");
		put(result, values, "u0#2793#working_storage/WS-MESSAGE.FILLER#596", 76,
			1, "SPACE");
		put(result, values, "u0#2839#working_storage/WS-MESSAGE.FILLER#597", 77,
			3, "SPACES");

		return result.toString();
	}
}
