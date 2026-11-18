package com.cardemo.reference;

import java.util.Objects;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transaction-types")
public final class TransactionTypeController
{
	public record UpdateRequest(String description)
	{
		public UpdateRequest
		{
			Objects.requireNonNull(description, "description");
		}
	}

	private final TransactionTypeListBrowseService browse;
	private final TransactionTypeMutationService mutations;

	public TransactionTypeController(TransactionTypeListBrowseService browse, TransactionTypeMutationService mutations)
	{
		this.browse = Objects.requireNonNull(browse, "browse");
		this.mutations = Objects.requireNonNull(mutations, "mutations");
	}

	@GetMapping
	public TransactionTypeListBrowseService.Page browse(
		@RequestParam(name = "startKey", defaultValue = "") String startKey,
		@RequestParam(name = "direction", defaultValue = "FORWARD") TransactionTypeListBrowseService.Direction direction)
	{
		return browse.browse(new TransactionTypeListBrowseService.Query(startKey, direction));
	}

	@PutMapping("/{key}")
	public TransactionTypeMutationService.UpdateOutcome update(@PathVariable String key,
		@RequestBody UpdateRequest request)
	{
		return mutations.update(new TransactionTypeMutationService.UpdateCommand(key,
			Objects.requireNonNull(request, "request").description()));
	}

	@DeleteMapping("/{key}")
	public TransactionTypeMutationService.DeleteOutcome delete(@PathVariable String key)
	{
		return mutations.delete(key);
	}
}
