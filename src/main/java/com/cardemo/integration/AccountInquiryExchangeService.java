package com.cardemo.integration;

import java.util.Objects;
import java.util.Optional;

public final class AccountInquiryExchangeService
{
	public record Request(String payload, String messageId)
	{
		public Request
		{
			payload = Objects.requireNonNull(payload, "payload");
			messageId = Objects.requireNonNull(messageId, "messageId");
		}
	}

	public record Reply(String payload, String correlationId)
	{
		public Reply
		{
			payload = Objects.requireNonNull(payload, "payload");
			correlationId = Objects.requireNonNull(correlationId,
				"correlationId");
		}
	}

	@FunctionalInterface
	public interface RequestPort { Optional<Request> receive(); }
	@FunctionalInterface
	public interface InquiryPort { String inquire(String payload); }
	@FunctionalInterface
	public interface ReplyPort { void send(Reply reply); }
	@FunctionalInterface
	public interface SyncpointPort { void commitBeforeReceive(); }

	private final SyncpointPort syncpoint;
	private final RequestPort requests;
	private final InquiryPort inquiry;
	private final ReplyPort replies;

	public AccountInquiryExchangeService(SyncpointPort syncpoint, RequestPort requests,
		InquiryPort inquiry, ReplyPort replies)
	{
		this.syncpoint = Objects.requireNonNull(syncpoint, "syncpoint");
		this.requests = Objects.requireNonNull(requests, "requests");
		this.inquiry = Objects.requireNonNull(inquiry, "inquiry");
		this.replies = Objects.requireNonNull(replies, "replies");
	}

	public boolean processNext()
	{
		syncpoint.commitBeforeReceive();
		Optional<Request> request = requests.receive();
		if (request.isEmpty()) return false;
		Request value = request.orElseThrow();
		String reply = inquiry.inquire(value.payload());
		replies.send(new Reply(reply, value.messageId()));
		return true;
	}
}
