package com.cardemo.account;

import java.util.Objects;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public final class AccountUpdateAggregateUpdateController
{
	public record UpdateRequest(
		AccountUpdateAggregateUpdateService.Aggregate prior,
		AccountUpdateAggregateUpdateService.Aggregate requested)
	{
		public UpdateRequest
		{
			Objects.requireNonNull(prior, "prior");
			Objects.requireNonNull(requested, "requested");
		}
	}

	public record UpdateResponse(
		AccountUpdateAggregateUpdateService.Outcome outcome,
		AccountUpdateAggregateUpdateService.Aggregate value)
	{
		public UpdateResponse
		{
			Objects.requireNonNull(outcome, "outcome");
		}
	}

	private final AccountUpdateAggregateUpdateService service;

	public AccountUpdateAggregateUpdateController(AccountUpdateAggregateUpdateService service)
	{
		this.service = Objects.requireNonNull(service, "service");
	}

	@PutMapping
	public UpdateResponse update(@RequestBody UpdateRequest request)
	{
		UpdateRequest value = Objects.requireNonNull(request, "request");
		var result = service.update(new AccountUpdateAggregateUpdateService.UpdateCommand(
			value.prior(), value.requested()));
		return new UpdateResponse(result.outcome(), result.value());
	}
}
