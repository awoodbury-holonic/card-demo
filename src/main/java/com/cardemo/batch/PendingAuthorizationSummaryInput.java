package com.cardemo.batch;

import java.io.IOException;

public final class PendingAuthorizationSummaryInput
{
	public static final int RECORD_LENGTH = 100;
	public static final int PAYLOAD_LENGTH = 100;
	public static final int RESERVED_LENGTH = 0;

	public static PendingAuthorizationSummaryInput decode(String image) throws IOException
	{
		if (image == null || image.length() != RECORD_LENGTH)
			throw new IOException("record image length");
		PendingAuthorizationSummaryInput record = new PendingAuthorizationSummaryInput();
		return record;
	}

}
