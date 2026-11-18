package com.cardemo.batch.account;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record Account(
	long accountId,
	String activeStatus,
	BigDecimal currentBalance,
	BigDecimal creditLimit,
	BigDecimal cashCreditLimit,
	LocalDate openDate,
	LocalDate expirationDate,
	LocalDate reissueDate,
	BigDecimal currentCycleCredit,
	BigDecimal currentCycleDebit,
	String postalCode,
	String groupId
)
{
	public static final int RECORD_LENGTH = 300;

	public long getAccountId()
	{
		return accountId;
	}

	public long getAcctId()
	{
		return accountId;
	}

	public String getActiveStatus()
	{
		return activeStatus;
	}

	public String getAcctActiveStatus()
	{
		return activeStatus;
	}

	public BigDecimal getCurrentBalance()
	{
		return currentBalance;
	}

	public BigDecimal getAcctCurrBal()
	{
		return currentBalance;
	}

	public BigDecimal getCreditLimit()
	{
		return creditLimit;
	}

	public BigDecimal getAcctCreditLimit()
	{
		return creditLimit;
	}

	public BigDecimal getCashCreditLimit()
	{
		return cashCreditLimit;
	}

	public BigDecimal getAcctCashCreditLimit()
	{
		return cashCreditLimit;
	}

	public LocalDate getOpenDate()
	{
		return openDate;
	}

	public LocalDate getAcctOpenDate()
	{
		return openDate;
	}

	public LocalDate getExpirationDate()
	{
		return expirationDate;
	}

	public LocalDate getAcctExpiraionDate()
	{
		return expirationDate;
	}

	public LocalDate getReissueDate()
	{
		return reissueDate;
	}

	public LocalDate getAcctReissueDate()
	{
		return reissueDate;
	}

	public BigDecimal getCurrentCycleCredit()
	{
		return currentCycleCredit;
	}

	public BigDecimal getAcctCurrCycCredit()
	{
		return currentCycleCredit;
	}

	public BigDecimal getCurrentCycleDebit()
	{
		return currentCycleDebit;
	}

	public BigDecimal getAcctCurrCycDebit()
	{
		return currentCycleDebit;
	}

	public String getPostalCode()
	{
		return postalCode;
	}

	public String getAcctAddrZip()
	{
		return postalCode;
	}

	public String getGroupId()
	{
		return groupId;
	}

	public String getAcctGroupId()
	{
		return groupId;
	}

	public static Account decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new Account(
			decodeInteger(image, 0, 11, 0, false),
			decodeAlphanumeric(image, 11, 1, 0, false),
			decodeDecimal(image, 12, 12, 2, true),
			decodeDecimal(image, 24, 12, 2, true),
			decodeDecimal(image, 36, 12, 2, true),
			decodeLocalDate(image, 48, 10, 0, false),
			decodeLocalDate(image, 58, 10, 0, false),
			decodeLocalDate(image, 68, 10, 0, false),
			decodeDecimal(image, 78, 12, 2, true),
			decodeDecimal(image, 90, 12, 2, true),
			decodeAlphanumeric(image, 102, 10, 0, false),
			decodeAlphanumeric(image, 112, 10, 0, false)
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

	private static LocalDate decodeLocalDate(String image, int offset,
		int length, int scale, boolean signed) throws IOException
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
