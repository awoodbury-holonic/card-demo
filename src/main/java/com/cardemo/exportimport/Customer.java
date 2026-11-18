package com.cardemo.exportimport;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record Customer(
	long custId,
	String custFirstName,
	String custMiddleName,
	String custLastName,
	String custAddrLine1,
	String custAddrLine2,
	String custAddrLine3,
	String custAddrStateCd,
	String custAddrCountryCd,
	String custAddrZip,
	String custPhoneNum1,
	String custPhoneNum2,
	long custSsn,
	String custGovtIssuedId,
	LocalDate custDobYyyyMmDd,
	String custEftAccountId,
	String custPriCardHolderInd,
	long custFicoCreditScore
)
{
	public static final int RECORD_LENGTH = 500;

	public long getCustId()
	{
		return custId;
	}

	public String getCustFirstName()
	{
		return custFirstName;
	}

	public String getCustMiddleName()
	{
		return custMiddleName;
	}

	public String getCustLastName()
	{
		return custLastName;
	}

	public String getCustAddrLine1()
	{
		return custAddrLine1;
	}

	public String getCustAddrLine2()
	{
		return custAddrLine2;
	}

	public String getCustAddrLine3()
	{
		return custAddrLine3;
	}

	public String getCustAddrStateCd()
	{
		return custAddrStateCd;
	}

	public String getCustAddrCountryCd()
	{
		return custAddrCountryCd;
	}

	public String getCustAddrZip()
	{
		return custAddrZip;
	}

	public String getCustPhoneNum1()
	{
		return custPhoneNum1;
	}

	public String getCustPhoneNum2()
	{
		return custPhoneNum2;
	}

	public long getCustSsn()
	{
		return custSsn;
	}

	public String getCustGovtIssuedId()
	{
		return custGovtIssuedId;
	}

	public LocalDate getCustDobYyyyMmDd()
	{
		return custDobYyyyMmDd;
	}

	public String getCustEftAccountId()
	{
		return custEftAccountId;
	}

	public String getCustPriCardHolderInd()
	{
		return custPriCardHolderInd;
	}

	public long getCustFicoCreditScore()
	{
		return custFicoCreditScore;
	}

	public static Customer decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new Customer(
			decodeInteger(image, 0, 9),
			decodeAlphanumeric(image, 9, 25),
			decodeAlphanumeric(image, 34, 25),
			decodeAlphanumeric(image, 59, 25),
			decodeAlphanumeric(image, 84, 50),
			decodeAlphanumeric(image, 134, 50),
			decodeAlphanumeric(image, 184, 50),
			decodeAlphanumeric(image, 234, 2),
			decodeAlphanumeric(image, 236, 3),
			decodeAlphanumeric(image, 239, 10),
			decodeAlphanumeric(image, 249, 15),
			decodeAlphanumeric(image, 264, 15),
			decodeInteger(image, 279, 9),
			decodeAlphanumeric(image, 288, 20),
			decodeLocalDate(image, 308, 10),
			decodeAlphanumeric(image, 318, 10),
			decodeAlphanumeric(image, 328, 1),
			decodeInteger(image, 329, 3)
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
