package com.cardemo.transaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TransactionListSelectionService
{

	public enum SelectionIntent
	{
		NONE, OPEN_DETAIL, INVALID
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

	private static <K> Optional<Selection<K>> firstPresent(
		List<Selection<K>> selections)
	{
		return List.copyOf(Objects.requireNonNull(
			selections, "selections")).stream()
			.filter(selection -> !selection.marker().isBlank()).findFirst();
	}

	public static <K> SelectionDecision<K> resolveSelection(
		List<Selection<K>> selections)
	{
		return firstPresent(selections).map(selection ->
			new SelectionDecision<>(
				selection.marker().equalsIgnoreCase(
					"S")
					? SelectionIntent.OPEN_DETAIL
					: SelectionIntent.INVALID,
				Optional.of(selection.key())))
			.orElseGet(() -> new SelectionDecision<>(
				SelectionIntent.NONE, Optional.empty()));
	}

	public record DetailNavigation<K>(String routePath, String keyName, K id)
	{
		public DetailNavigation
		{
			Objects.requireNonNull(routePath, "routePath");
			Objects.requireNonNull(keyName, "keyName");
			Objects.requireNonNull(id, "id");
			if (routePath.isBlank() || keyName.isBlank())
				throw new IllegalArgumentException("detail route is incomplete");
		}
	}

	public static <K> Optional<DetailNavigation<K>> detailNavigation(
		SelectionDecision<K> decision)
	{
		Objects.requireNonNull(decision, "decision");
		if (decision.intent() != SelectionIntent.OPEN_DETAIL)
			return Optional.empty();
		return Optional.of(new DetailNavigation<>(
			"/api/transactions/{id}",
			"id",
			decision.key().orElseThrow()));
	}
}
