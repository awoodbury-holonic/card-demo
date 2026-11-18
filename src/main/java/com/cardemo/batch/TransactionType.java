package com.cardemo.batch;

import java.io.IOException;

public record TransactionType(
	String code,
	String description
)
{
	public static final int RECORD_LENGTH = 60;

	public String getTranType()
	{
		return code;
	}

	public String getTranTypeDesc()
	{
		return description;
	}

	public static TransactionType decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new TransactionType(
			decodeAlphanumeric(image, 0, 2),
			decodeAlphanumeric(image, 2, 50)
		);
	}

	private static String decodeAlphanumeric(String image, int offset, int length)
	{
		return image.substring(offset, offset + length);
	}
}
