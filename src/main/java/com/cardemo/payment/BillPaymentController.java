package com.cardemo.payment;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bill-payments")
public final class BillPaymentController
{
	public record PaymentRequest(String accountId, String decision) { }
	public record PaymentResponse(String outcome,
		Optional<BigDecimal> previousBalance,
		Optional<BigDecimal> remainingBalance,
		Optional<Long> transactionId) { }

	private final BillPaymentPaymentService service;

	public BillPaymentController(BillPaymentPaymentService service)
	{
		this.service = Objects.requireNonNull(service, "service");
	}

	@PostMapping
	public PaymentResponse pay(@RequestBody PaymentRequest request)
	{
		PaymentRequest value = Objects.requireNonNull(request, "request");
		if (value.accountId() == null || value.accountId().isBlank())
			return empty("INVALID_REQUEST");
		BillPaymentPaymentService.Decision decision;
		try
		{
			decision = BillPaymentPaymentService.Decision.valueOf(
				String.valueOf(value.decision()).trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException failure)
		{
			return empty("INVALID_REQUEST");
		}
		var result = service.pay(new BillPaymentPaymentService.PaymentRequest(
			value.accountId().trim(), decision));
		return new PaymentResponse(result.outcome().name(), result.previousBalance(),
			result.remainingBalance(), result.transactionId());
	}

	private static PaymentResponse empty(String outcome)
	{
		return new PaymentResponse(outcome, Optional.empty(),
			Optional.empty(), Optional.empty());
	}
}
