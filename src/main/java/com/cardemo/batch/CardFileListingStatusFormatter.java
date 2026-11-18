package com.cardemo.batch;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

public final class CardFileListingStatusFormatter
{
	public interface HostStatusTailCodec
	{
		String threeDigitTail(String secondStatusCharacter);
	}

	private final HostStatusTailCodec hostStatusTailCodec;

	public CardFileListingStatusFormatter(HostStatusTailCodec hostStatusTailCodec)
	{
		if (hostStatusTailCodec == null)
			throw new IllegalArgumentException("host status tail codec is required");
		this.hostStatusTailCodec = hostStatusTailCodec;
	}

	public CardFileListingStatusFormatter(Charset hostCharset)
	{
		this(statusDetail -> threeDigitHostTail(statusDetail, hostCharset));
	}

	private static String threeDigitHostTail(String statusDetail,
		Charset hostCharset)
	{
		if (hostCharset == null || statusDetail == null || statusDetail.length() != 1)
			throw new IllegalArgumentException("one host status character is required");
		try
		{
			ByteBuffer bytes = hostCharset.newEncoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.encode(CharBuffer.wrap(statusDetail));
			if (bytes.remaining() != 1)
				throw new IllegalArgumentException("host status character must encode to one byte");
			int unsignedRightByte = bytes.get() & 0xff;
			return String.format(Locale.ROOT, "%03d", unsignedRightByte);
		}
		catch (CharacterCodingException failure)
		{
			throw new IllegalArgumentException("unmappable host status character", failure);
		}
	}

	public String formatStatus(String status)
	{
		if (status == null || status.length() != 2)
			throw new IllegalArgumentException("status must be two characters");
		char statusClass = status.charAt(0);
		char statusDetail = status.charAt(1);
		boolean numeric = statusClass >= '0' && statusClass <= '9' &&
			statusDetail >= '0' && statusDetail <= '9';
		if (numeric && statusClass != "9".charAt(0))
			return "FILE STATUS IS: NNNN" +
				"0000".substring(0, 2) + status;
		String tail = hostStatusTailCodec.threeDigitTail(status.substring(1));
		if (tail == null || tail.length() != 3 ||
			tail.charAt(0) < '0' || tail.charAt(0) > '9' ||
			tail.charAt(1) < '0' || tail.charAt(1) > '9' ||
			tail.charAt(2) < '0' || tail.charAt(2) > '9')
			throw new IllegalArgumentException("host status tail must be three digits");
		return "FILE STATUS IS: NNNN" + statusClass + tail;
	}

}
