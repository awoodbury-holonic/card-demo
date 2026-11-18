package com.cardemo.user;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class UserListSelectionService
{

	public enum SelectionIntent
	{
		NONE, UPDATE, DELETE, INVALID
	}

	public record Selection<K>(String marker, K key)
	{
		public Selection
		{
			Objects.requireNonNull(marker, "marker");
			Objects.requireNonNull(key, "key");
		}
	}

	public record SelectionDecision<K>(SelectionIntent intent,
		Optional<K> key)
	{
		public SelectionDecision
		{
			Objects.requireNonNull(intent, "intent");
			key = Objects.requireNonNull(key, "key");
		}
	}

	public static <K> SelectionDecision<K> resolveSelection(
		List<Selection<K>> selections)
	{
		Optional<Selection<K>> selected = List.copyOf(
			Objects.requireNonNull(selections, "selections")).stream()
			.filter(selection -> !selection.marker().isBlank()).findFirst();
		if (selected.isEmpty())
			return new SelectionDecision<>(SelectionIntent.NONE,
				Optional.empty());
		Selection<K> value = selected.orElseThrow();
		SelectionIntent intent;
		if (value.marker().equalsIgnoreCase(
			"U"))
			intent = SelectionIntent.UPDATE;
		else if (value.marker().equalsIgnoreCase(
			"D"))
			intent = SelectionIntent.DELETE;
		else
			intent = SelectionIntent.INVALID;
		return new SelectionDecision<>(intent,
			Optional.of(value.key()));
	}
}
