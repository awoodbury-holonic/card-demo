package com.cardemo.batch;

import java.io.IOException;

public record ReportDateRange(
	String wsStartDate,
	String wsEndDate
)
{
	public static final int RECORD_LENGTH = 21;

	public String getWsStartDate()
	{
		return wsStartDate;
	}

	public String getWsEndDate()
	{
		return wsEndDate;
	}

	public static ReportDateRange decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new ReportDateRange(
			decodeAlphanumeric(image, 0, 10),
			decodeAlphanumeric(image, 11, 10)
		);
	}

	private static String decodeAlphanumeric(String image, int offset, int length)
	{
		return image.substring(offset, offset + length);
	}
}
