package com.cardemo.transaction;

import java.util.Objects;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public final class TransactionController
{
	private final TransactionListPageService service;

	public TransactionController(TransactionListPageService service)
	{
		this.service = service;
	}

	@GetMapping
	public TransactionListPageService.BrowsePage<TransactionRow,String> browse(
		@RequestParam(name = "startKey") String startKey,
		@RequestParam(name = "direction") TransactionListPageService.Direction direction,
		@RequestParam(name = "anchorMode") TransactionListPageService.AnchorMode anchorMode)
	{
		return service.browse(new TransactionListPageService.KeyedPageQuery<>(
			startKey, direction,
			anchorMode));
	}
	private long cdemoPgmContext;



	public enum PageMove { PREVIOUS, NEXT }
	public enum PageOutcome { PRESENT, BOUNDARY, FAILED }

	public record PageWindow<K>(int number, Optional<K> firstKey,
		Optional<K> lastKey, boolean hasNext)
	{
		public PageWindow
		{
			if (number < 0)
				throw new IllegalArgumentException("page number is negative");
			firstKey = Objects.requireNonNull(firstKey, "firstKey");
			lastKey = Objects.requireNonNull(lastKey, "lastKey");
		}
	}

	public record PageResponse<T,K>(PageOutcome outcome,
		Optional<TransactionListPageService.BrowsePage<T,K>> page,
		PageWindow<K> window, String feedback)
	{
		public PageResponse
		{
			Objects.requireNonNull(outcome, "outcome");
			page = Objects.requireNonNull(page, "page");
			Objects.requireNonNull(window, "window");
			Objects.requireNonNull(feedback, "feedback");
		}
	}

	public PageResponse<TransactionRow,String> browsePage(
		PageMove move, PageWindow<String> window)
	{
		Objects.requireNonNull(move, "move");
		Objects.requireNonNull(window, "window");
		if (move == PageMove.PREVIOUS && window.number() <= 1)
			return new PageResponse<>(PageOutcome.BOUNDARY,
				Optional.empty(), window,
				"You are already at the top of the page...");
		if (move == PageMove.NEXT && !window.hasNext())
			return new PageResponse<>(PageOutcome.BOUNDARY,
				Optional.empty(), window,
				"You are already at the bottom of the page...");

		String anchor = (move == PageMove.PREVIOUS
			? window.firstKey() : window.lastKey()).orElseThrow(
				() -> new IllegalArgumentException("page anchor is missing"));
		TransactionListPageService.Direction direction =
			move == PageMove.PREVIOUS
				? TransactionListPageService.Direction.BACKWARD
				: TransactionListPageService.Direction.FORWARD;
		TransactionListPageService.BrowsePage<TransactionRow,
			String> page = service.browse(
				new TransactionListPageService.KeyedPageQuery<>(anchor,
					direction,
					TransactionListPageService.AnchorMode.EXCLUDE_ANCHOR));
		if (page.outcome() == TransactionListPageService.BrowseOutcome.SUCCESS)
			return new PageResponse<>(PageOutcome.PRESENT,
				Optional.of(page),
				new PageWindow<>(move == PageMove.PREVIOUS
					? Math.max(1, window.number() - 1)
					: window.number() + 1,
					Optional.ofNullable(page.firstKey()),
					Optional.ofNullable(page.lastKey()),
					page.hasNext()), "");
		if (page.outcome() == TransactionListPageService.BrowseOutcome.UNEXPECTED)
			return new PageResponse<>(PageOutcome.FAILED,
				Optional.empty(), window,
				"Unable to lookup transaction...");
		return new PageResponse<>(PageOutcome.BOUNDARY,
			Optional.empty(), window,
			move == PageMove.PREVIOUS
				? "You have reached the top of the page..."
				: "You have reached the bottom of the page...");
	}

	public enum TransferContext { RESET }

	public record TransferContinuation(String fromTransactionId,
		String fromProgram, TransferContext context)
	{
		public TransferContinuation
		{
			Objects.requireNonNull(fromTransactionId, "fromTransactionId");
			Objects.requireNonNull(fromProgram, "fromProgram");
			Objects.requireNonNull(context, "context");
		}
	}

	public record TransferNavigation(String destination,
		TransferContinuation continuation)
	{
		public TransferNavigation
		{
			Objects.requireNonNull(destination, "destination");
			Objects.requireNonNull(continuation, "continuation");
			if (destination.isBlank())
				throw new IllegalArgumentException("destination is required");
		}
	}

	public static TransferNavigation planTransfer(
		Optional<String> normalizedDestination,
		String fromTransactionId, String fromProgram)
	{
		String destination = Objects.requireNonNull(normalizedDestination,
			"normalizedDestination").orElse("COSGN00C");
		return new TransferNavigation(destination,
			new TransferContinuation(fromTransactionId, fromProgram,
				TransferContext.RESET));
	}

	public record SelectionFeedback(String message, int focusIndex)
	{
		public SelectionFeedback
		{
			Objects.requireNonNull(message, "message");
			if (message.isBlank())
				throw new IllegalArgumentException("selection feedback is empty");
		}
	}

	public record DeferredSelectionDisplay(TransactionListSelectionService.SelectionIntent intent,
		Optional<SelectionFeedback> feedback)
	{
		public DeferredSelectionDisplay
		{
			Objects.requireNonNull(intent, "intent");
			feedback = Objects.requireNonNull(feedback, "feedback");
		}

		public DeferredSelectionDisplay afterNumericValidation(
			boolean numericInputInvalid)
		{
			if (!numericInputInvalid ||
				intent != TransactionListSelectionService.SelectionIntent.INVALID)
				return this;
			return new DeferredSelectionDisplay(intent,
				Optional.of(new SelectionFeedback(
					"Tran ID must be Numeric ...", -1)));
		}

		public Optional<SelectionFeedback> pageForwardDisplay()
		{
			return feedback;
		}
	}

	public static DeferredSelectionDisplay deferInvalidSelection(
		TransactionListSelectionService.SelectionIntent intent)
	{
		Objects.requireNonNull(intent, "intent");
		return new DeferredSelectionDisplay(intent,
			intent == TransactionListSelectionService.SelectionIntent.INVALID
				? Optional.of(new SelectionFeedback(
					"Invalid selection. Valid value is S", -1))
				: Optional.empty());
	}
}
