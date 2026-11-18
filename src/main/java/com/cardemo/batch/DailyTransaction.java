package com.cardemo.batch;

import java.io.IOException;
import java.math.BigDecimal;

public record DailyTransaction(
	String transactionId,
	String typeCode,
	long categoryCode,
	String source,
	String description,
	BigDecimal amount,
	long merchantId,
	String merchantName,
	String merchantCity,
	String merchantZip,
	String cardNumber,
	String originTimestamp,
	String processedTimestamp
)
{
	public static final int RECORD_LENGTH = 350;

	public String getTransactionId()
	{
		return transactionId;
	}

	public String getDalytranId()
	{
		return transactionId;
	}

	public String getTypeCode()
	{
		return typeCode;
	}

	public String getDalytranTypeCd()
	{
		return typeCode;
	}

	public long getCategoryCode()
	{
		return categoryCode;
	}

	public long getDalytranCatCd()
	{
		return categoryCode;
	}

	public String getSource()
	{
		return source;
	}

	public String getDalytranSource()
	{
		return source;
	}

	public String getDescription()
	{
		return description;
	}

	public String getDalytranDesc()
	{
		return description;
	}

	public BigDecimal getAmount()
	{
		return amount;
	}

	public BigDecimal getDalytranAmt()
	{
		return amount;
	}

	public long getMerchantId()
	{
		return merchantId;
	}

	public long getDalytranMerchantId()
	{
		return merchantId;
	}

	public String getMerchantName()
	{
		return merchantName;
	}

	public String getDalytranMerchantName()
	{
		return merchantName;
	}

	public String getMerchantCity()
	{
		return merchantCity;
	}

	public String getDalytranMerchantCity()
	{
		return merchantCity;
	}

	public String getMerchantZip()
	{
		return merchantZip;
	}

	public String getDalytranMerchantZip()
	{
		return merchantZip;
	}

	public String getCardNumber()
	{
		return cardNumber;
	}

	public String getDalytranCardNum()
	{
		return cardNumber;
	}

	public String getOriginTimestamp()
	{
		return originTimestamp;
	}

	public String getDalytranOrigTs()
	{
		return originTimestamp;
	}

	public String getProcessedTimestamp()
	{
		return processedTimestamp;
	}

	public String getDalytranProcTs()
	{
		return processedTimestamp;
	}

	public static DailyTransaction decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new DailyTransaction(
			decodeAlphanumeric(image, 0, 16, 0, false),
			decodeAlphanumeric(image, 16, 2, 0, false),
			decodeInteger(image, 18, 4, 0, false),
			decodeAlphanumeric(image, 22, 10, 0, false),
			decodeAlphanumeric(image, 32, 100, 0, false),
			decodeDecimal(image, 132, 11, 2, true),
			decodeInteger(image, 143, 9, 0, false),
			decodeAlphanumeric(image, 152, 50, 0, false),
			decodeAlphanumeric(image, 202, 50, 0, false),
			decodeAlphanumeric(image, 252, 10, 0, false),
			decodeAlphanumeric(image, 262, 16, 0, false),
			decodeAlphanumeric(image, 278, 26, 0, false),
			decodeAlphanumeric(image, 304, 26, 0, false)
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
