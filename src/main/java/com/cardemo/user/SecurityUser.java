package com.cardemo.user;

import java.io.IOException;

public record SecurityUser(
	String secUsrId,
	String secUsrFname,
	String secUsrLname,
	String secUsrPwd,
	String secUsrType,
	String secUsrFiller
)
{
	public static final int RECORD_LENGTH = 80;

	public String getSecUsrId()
	{
		return secUsrId;
	}

	public String getSecUsrFname()
	{
		return secUsrFname;
	}

	public String getSecUsrLname()
	{
		return secUsrLname;
	}

	public String getSecUsrPwd()
	{
		return secUsrPwd;
	}

	public String getSecUsrType()
	{
		return secUsrType;
	}

	public String getSecUsrFiller()
	{
		return secUsrFiller;
	}

	public static SecurityUser decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		return new SecurityUser(
			decodeAlphanumeric(image, 0, 8),
			decodeAlphanumeric(image, 8, 20),
			decodeAlphanumeric(image, 28, 20),
			decodeAlphanumeric(image, 48, 8),
			decodeAlphanumeric(image, 56, 1),
			decodeAlphanumeric(image, 57, 23)
		);
	}

	private static String decodeAlphanumeric(String image, int offset, int length)
	{
		return image.substring(offset, offset + length);
	}
}
