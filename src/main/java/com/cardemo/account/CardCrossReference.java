package com.cardemo.account;

import java.io.IOException;

public record CardCrossReference(
	String xrefCardNum,
	long xrefCustId,
	long xrefAcctId
)
{
	public static final int RECORD_LENGTH = 50;

	public String getXrefCardNum()
	{
		return xrefCardNum;
	}

	public long getXrefCustId()
	{
		return xrefCustId;
	}

	public long getXrefAcctId()
	{
		return xrefAcctId;
	}

	public static CardCrossReference decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new CardCrossReference(
			decodeAlphanumeric(image, 0, 16),
			decodeInteger(image, 16, 9),
			decodeInteger(image, 25, 11)
		);
	}

	private static String decodeAlphanumeric(String image, int offset, int length)
	{
		return image.substring(offset, offset + length);
	}

	private static long decodeInteger(String image, int offset, int length)
		throws IOException
	{
		String value = image.substring(offset, offset + length);
		for (int i = 0; i < value.length(); ++i)
			if (value.charAt(i) < '0' || value.charAt(i) > '9')
				throw new IOException("non-decimal display field");
		try
		{
			return Long.parseLong(value);
		}
		catch (NumberFormatException failure)
		{
			throw new IOException("display integer exceeds target range", failure);
		}
	}
}
