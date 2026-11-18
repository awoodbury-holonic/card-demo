package com.cardemo.reference;

import java.io.IOException;

public record TransactionTypeChangeInput(
	String inputRecType,
	String inputRecNumber,
	String inputRecDesc
)
{
	public static final int RECORD_LENGTH = 53;

	public String getInputRecType()
	{
		return inputRecType;
	}

	public String getInputRecNumber()
	{
		return inputRecNumber;
	}

	public String getInputRecDesc()
	{
		return inputRecDesc;
	}

	public static TransactionTypeChangeInput decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new TransactionTypeChangeInput(
			decodeAlphanumeric(image, 0, 1),
			decodeAlphanumeric(image, 1, 2),
			decodeAlphanumeric(image, 3, 50)
		);
	}

	private static String decodeAlphanumeric(String image, int offset, int length)
	{
		return image.substring(offset, offset + length);
	}
}
