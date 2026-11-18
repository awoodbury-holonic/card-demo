package com.cardemo.batch;

import java.io.IOException;
import java.math.BigDecimal;

public record TransactionCategoryBalance(
	long accountId,
	String typeCode,
	long categoryCode,
	BigDecimal balance
)
{
	public static final int RECORD_LENGTH = 50;

	public long getAccountId()
	{
		return accountId;
	}

	public long getTrancatAcctId()
	{
		return accountId;
	}

	public String getTypeCode()
	{
		return typeCode;
	}

	public String getTrancatTypeCd()
	{
		return typeCode;
	}

	public long getCategoryCode()
	{
		return categoryCode;
	}

	public long getTrancatCd()
	{
		return categoryCode;
	}

	public BigDecimal getBalance()
	{
		return balance;
	}

	public BigDecimal getTranCatBal()
	{
		return balance;
	}

	public static TransactionCategoryBalance decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new TransactionCategoryBalance(
			decodeInteger(image, 0, 11, 0, false),
			decodeAlphanumeric(image, 11, 2, 0, false),
			decodeInteger(image, 13, 4, 0, false),
			decodeDecimal(image, 17, 11, 2, true)
		);
	}

	private static String decodeAlphanumeric(String image, int offset, int length,
		int scale, boolean signed)
	{
		return image.substring(offset, offset + length);
	}

	private static long decodeInteger(String image, int offset, int length,
		int scale, boolean signed) throws IOException
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

	private static BigDecimal decodeDecimal(String image, int offset,
		int length, int scale, boolean signed) throws IOException
	{
		String value = image.substring(offset, offset + length);
		boolean negative = false;
		int start = 0;
		if (!value.isEmpty() && (value.charAt(0) == '-' || value.charAt(0) == '+'))
		{
			if (!signed) throw new IOException("unexpected display sign");
			negative = value.charAt(0) == '-';
			start = 1;
		}
		if (signed && start == 0 && !value.isEmpty())
		{
			char overpunch = value.charAt(value.length() - 1);
			int digit = overpunch == '{' || overpunch == '}' ? 0
				: overpunch >= 'A' && overpunch <= 'I' ? overpunch - 'A' + 1
				: overpunch >= 'J' && overpunch <= 'R' ? overpunch - 'J' + 1
				: -1;
			if (digit >= 0)
			{
				negative = overpunch == '}' ||
					(overpunch >= 'J' && overpunch <= 'R');
				value = value.substring(0, value.length() - 1) + digit;
			}
		}
		for (int i = start; i < value.length(); ++i)
			if (value.charAt(i) < '0' || value.charAt(i) > '9')
				throw new IOException("non-decimal display field");
		try
		{
			BigDecimal decoded = new BigDecimal(
				value.substring(start)).movePointLeft(scale);
			return negative ? decoded.negate() : decoded;
		}
		catch (NumberFormatException failure)
		{
			throw new IOException("invalid display decimal", failure);
		}
	}
}
