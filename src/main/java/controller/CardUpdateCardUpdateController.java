package com.cardemo.card;

import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cards")
public final class CardUpdateCardUpdateController
{
	public record HttpUpdateRequest(CardUpdateOptimisticUpdateService.CardUpdateSnapshot expected,
		CardUpdateOptimisticUpdateService.CardUpdateSnapshot proposed)
	{
		public HttpUpdateRequest
		{
			Objects.requireNonNull(expected, "expected");
			Objects.requireNonNull(proposed, "proposed");
		}
	}

	private final CardUpdateOptimisticUpdateService updates;

	public CardUpdateCardUpdateController(CardUpdateOptimisticUpdateService updates)
	{
		this.updates = Objects.requireNonNull(updates, "updates");
	}

	@GetMapping("/{cardNumber}/update-snapshot")
	public ResponseEntity<CardUpdateOptimisticUpdateService.CardUpdateSnapshot> current(
		@PathVariable("cardNumber") String key)
	{
		return updates.findCurrent(key).map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PutMapping("/{cardNumber}")
	public ResponseEntity<CardUpdateOptimisticUpdateService.UpdateResult> update(
		@PathVariable("cardNumber") String key,
		@RequestBody HttpUpdateRequest request)
	{
		CardUpdateOptimisticUpdateService.UpdateResult result = updates.updateIfUnchanged(
			new CardUpdateOptimisticUpdateService.UpdateRequest(key, request.expected(),
				request.proposed()));
		return switch (result.outcome())
		{
			case UPDATED -> ResponseEntity.ok(result);
			case INVALID_INPUT -> ResponseEntity.badRequest().body(result);
			case LOCK_FAILED -> ResponseEntity.notFound().build();
			case CONCURRENT_CHANGE -> ResponseEntity.status(409).body(result);
			case UPDATE_FAILED -> ResponseEntity.internalServerError().body(result);
		};
	}
}
