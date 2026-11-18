package com.cardemo.batch;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record Card(
	String number,
	long accountId,
	long cvv,
	String embossedName,
	LocalDate expirationDate,
	String activeStatus
)
{
	public static final int RECORD_LENGTH = 150;

	public String getCardNum()
	{
		return number;
	}

	public long getCardAcctId()
	{
		return accountId;
	}

	public long getCardCvvCd()
	{
		return cvv;
	}

	public String getCardEmbossedName()
	{
		return embossedName;
	}

	public LocalDate getCardExpiraionDate()
	{
		return expirationDate;
	}

	public String getCardActiveStatus()
	{
		return activeStatus;
	}

	public static Card decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new Card(
			decodeAlphanumeric(image, 0, 16),
			decodeInteger(image, 16, 11),
			decodeInteger(image, 27, 3),
			decodeAlphanumeric(image, 30, 50),
			decodeLocalDate(image, 80, 10),
			decodeAlphanumeric(image, 90, 1)
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

	private static LocalDate decodeLocalDate(String image, int offset,
		int length) throws IOException
	{
		String value = image.substring(offset, offset + length);
		if (value.isBlank() || value.chars().allMatch(ch -> ch == 0))
			return null;
		try
		{
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException failure)
		{
			throw new IOException("invalid ISO date at offset " + offset, failure);
		}
	}
}
