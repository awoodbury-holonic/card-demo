package com.cardemo.card;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class CardListController
{
	private record AidDecodeRow(String sourceAid, String namedCondition) { }

	private static final List<AidDecodeRow> AID_DECODE_ROWS =
		List.of(
			new AidDecodeRow("DFHENTER",
				"CCARD-AID-ENTER"),
			new AidDecodeRow("DFHCLEAR",
				"CCARD-AID-CLEAR"),
			new AidDecodeRow("DFHPA1",
				"CCARD-AID-PA1"),
			new AidDecodeRow("DFHPA2",
				"CCARD-AID-PA2"),
			new AidDecodeRow("DFHPF1",
				"CCARD-AID-PFK01"),
			new AidDecodeRow("DFHPF2",
				"CCARD-AID-PFK02"),
			new AidDecodeRow("DFHPF3",
				"CCARD-AID-PFK03"),
			new AidDecodeRow("DFHPF4",
				"CCARD-AID-PFK04"),
			new AidDecodeRow("DFHPF5",
				"CCARD-AID-PFK05"),
			new AidDecodeRow("DFHPF6",
				"CCARD-AID-PFK06"),
			new AidDecodeRow("DFHPF7",
				"CCARD-AID-PFK07"),
			new AidDecodeRow("DFHPF8",
				"CCARD-AID-PFK08"),
			new AidDecodeRow("DFHPF9",
				"CCARD-AID-PFK09"),
			new AidDecodeRow("DFHPF10",
				"CCARD-AID-PFK10"),
			new AidDecodeRow("DFHPF11",
				"CCARD-AID-PFK11"),
			new AidDecodeRow("DFHPF12",
				"CCARD-AID-PFK12"),
			new AidDecodeRow("DFHPF13",
				"CCARD-AID-PFK01"),
			new AidDecodeRow("DFHPF14",
				"CCARD-AID-PFK02"),
			new AidDecodeRow("DFHPF15",
				"CCARD-AID-PFK03"),
			new AidDecodeRow("DFHPF16",
				"CCARD-AID-PFK04"),
			new AidDecodeRow("DFHPF17",
				"CCARD-AID-PFK05"),
			new AidDecodeRow("DFHPF18",
				"CCARD-AID-PFK06"),
			new AidDecodeRow("DFHPF19",
				"CCARD-AID-PFK07"),
			new AidDecodeRow("DFHPF20",
				"CCARD-AID-PFK08"),
			new AidDecodeRow("DFHPF21",
				"CCARD-AID-PFK09"),
			new AidDecodeRow("DFHPF22",
				"CCARD-AID-PFK10"),
			new AidDecodeRow("DFHPF23",
				"CCARD-AID-PFK11"),
			new AidDecodeRow("DFHPF24",
				"CCARD-AID-PFK12")
		);

	public static Optional<String> normalizeAidSymbol(String sourceAid)
	{
		Objects.requireNonNull(sourceAid, "sourceAid");
		for (AidDecodeRow row : AID_DECODE_ROWS)
			if (row.sourceAid().equals(sourceAid))
				return Optional.of(row.namedCondition());
		return Optional.empty();
	}
	public enum OptionalFilterOutcome { ABSENT, INVALID, VALID }

	public record OptionalFilterProfile(int width, String invalidMessage)
	{
		public OptionalFilterProfile
		{
			if (width < 1 || width > 32)
				throw new IllegalArgumentException("source-bounded width required");
			Objects.requireNonNull(invalidMessage, "invalidMessage");
		}
	}

	public record NumericFilter(String digits, int width)
	{
		public NumericFilter
		{
			Objects.requireNonNull(digits, "digits");
			if (digits.length() != width || !digits.chars().allMatch(
					ch -> ch >= '0' && ch <= '9')
				|| digits.chars().allMatch(ch -> ch == '0'))
				throw new IllegalArgumentException("numeric filter invariant");
		}
	}

	public record OptionalFilterValidation(OptionalFilterOutcome outcome,
		Optional<NumericFilter> key,
		Optional<String> error)
	{
		public OptionalFilterValidation
		{
			Objects.requireNonNull(outcome, "outcome");
			key = Objects.requireNonNull(key, "key");
			error = Objects.requireNonNull(error, "error");
			if ((outcome == OptionalFilterOutcome.VALID) != key.isPresent()
				|| (outcome == OptionalFilterOutcome.INVALID) != error.isPresent())
				throw new IllegalArgumentException("filter outcome mismatch");
		}
	}

	public record OrderedFilterValidation(OptionalFilterValidation accountId,
		OptionalFilterValidation cardNumber, Optional<String> firstError)
	{
		public OrderedFilterValidation
		{
			Objects.requireNonNull(accountId, "accountId");
			Objects.requireNonNull(cardNumber, "cardNumber");
			firstError = Objects.requireNonNull(firstError, "firstError");
			if (!firstError.equals(accountId.error().or(() -> cardNumber.error())))
				throw new IllegalArgumentException("source error order mismatch");
		}
	}

	public static OptionalFilterValidation validateOptionalFilter(
		Optional<String> input, OptionalFilterProfile profile)
	{
		String text = Objects.requireNonNull(input, "input").orElse("");
		var source = Objects.requireNonNull(profile, "profile");
		if (text.isBlank() || text.chars().allMatch(ch -> ch == '\0')
			|| (text.length() == source.width()
				&& text.chars().allMatch(ch -> ch == '0')))
			return new OptionalFilterValidation(OptionalFilterOutcome.ABSENT,
				Optional.empty(), Optional.empty());
		if (text.length() != source.width()
			|| !text.chars().allMatch(ch -> ch >= '0' && ch <= '9'))
			return new OptionalFilterValidation(OptionalFilterOutcome.INVALID,
				Optional.empty(), Optional.of(source.invalidMessage()));
		return new OptionalFilterValidation(OptionalFilterOutcome.VALID,
			Optional.of(new NumericFilter(text, source.width())),
			Optional.empty());
	}

	public static OptionalFilterProfile accountFilterProfile()
	{
		return new OptionalFilterProfile(11,
			"ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
	}

	public static OptionalFilterProfile cardFilterProfile()
	{
		return new OptionalFilterProfile(16,
			"CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
	}

	public static OrderedFilterValidation validateOrderedFilters(
		Optional<String> accountId, Optional<String> cardNumber)
	{
		var accountIdResult = validateOptionalFilter(accountId,
			accountFilterProfile());
		var cardNumberResult = validateOptionalFilter(cardNumber,
			cardFilterProfile());
		return new OrderedFilterValidation(accountIdResult, cardNumberResult,
			accountIdResult.error().or(() -> cardNumberResult.error()));
	}
	public record BrowseCriteria(
		Optional<NumericFilter> accountId,
		Optional<NumericFilter> cardNumber)
	{
		public BrowseCriteria
		{
			accountId = Objects.requireNonNull(accountId, "accountId filter");
			cardNumber = Objects.requireNonNull(cardNumber, "cardNumber filter");
		}
	}

	public record FilteredBrowseQuery(BrowseCriteria criteria,
		Optional<CardKey> anchor,
		BrowseDirection direction, int limit)
	{
		public FilteredBrowseQuery
		{
			Objects.requireNonNull(criteria, "criteria");
			anchor = Objects.requireNonNull(anchor, "anchor");
			Objects.requireNonNull(direction, "direction");
			if (limit < 1 || limit > 7)
				throw new IllegalArgumentException("source-bounded page limit required");
		}
	}

	public interface FilteredCardBrowsePort
	{
		BrowsePage browse(FilteredBrowseQuery query);
	}

	public static boolean matchesFilteredRow(CardRow row,
		BrowseCriteria criteria)
	{
		Objects.requireNonNull(row, "row");
		Objects.requireNonNull(criteria, "criteria");
		return criteria.accountId().map(key ->
				row.accountId().equals(key.digits())).orElse(true)
			&& criteria.cardNumber().map(key ->
				row.cardNumber().equals(key.digits())).orElse(true);
	}
	public record ReceivedInput(
		Optional<String> accountFilter,
		Optional<String> cardFilter,
		List<String> selectionValues)
	{
		public ReceivedInput
		{
			accountFilter = Objects.requireNonNull(
				accountFilter, "accountFilter");
			cardFilter = Objects.requireNonNull(
				cardFilter, "cardFilter");
			selectionValues = List.copyOf(
				Objects.requireNonNull(selectionValues,
					"selectionValues"));
			if (selectionValues.size() != 7)
				throw new IllegalArgumentException("source selection count mismatch");
			for (String value : selectionValues)
				Objects.requireNonNull(value, "selection value");
		}
	}

	public interface ScreenPort
	{
		ReceivedInput receive();
	}

	public record ReceivedInputValidation(
		OrderedFilterValidation filters,
		SelectionValidation selection)
	{
		public ReceivedInputValidation
		{
			Objects.requireNonNull(filters, "filters");
			Objects.requireNonNull(selection, "selection");
		}

		public boolean valid()
		{
			return filters.firstError().isEmpty() && selection.error().isEmpty();
		}
	}

	public static ReceivedInputValidation validateReceivedInput(
		ReceivedInput input)
	{
		var source = Objects.requireNonNull(input, "input");
		return new ReceivedInputValidation(
			validateOrderedFilters(source.accountFilter(),
				source.cardFilter()),
			validateSelectedAction(source.selectionValues()));
	}
	public enum CardAction
	{
		UPDATE, DETAIL
	}

	public record SelectedCard(int rowIndex,
		CardAction action)
	{
		public SelectedCard
		{
			if (rowIndex < 0)
				throw new IllegalArgumentException("rowIndex must be nonnegative");
			Objects.requireNonNull(action, "action");
		}
	}

	public enum SelectionError
	{
		INVALID, MULTIPLE
	}

	public record SelectionValidation(
		Optional<SelectedCard> selected,
		Optional<SelectionError> error)
	{
		public SelectionValidation
		{
			selected = Objects.requireNonNull(selected, "selected");
			error = Objects.requireNonNull(error, "error");
			if (selected.isPresent() && error.isPresent())
				throw new IllegalArgumentException("selection cannot be both valid and rejected");
		}

		public static SelectionValidation none()
		{
			return new SelectionValidation(Optional.empty(),
				Optional.empty());
		}

		public static SelectionValidation invalid()
		{
			return new SelectionValidation(Optional.empty(),
				Optional.of(SelectionError.INVALID));
		}

		public static SelectionValidation multiple()
		{
			return new SelectionValidation(Optional.empty(),
				Optional.of(SelectionError.MULTIPLE));
		}

		public static SelectionValidation selected(int rowIndex,
			CardAction action)
		{
			return new SelectionValidation(Optional.of(
				new SelectedCard(rowIndex, action)),
				Optional.empty());
		}
	}

	public static SelectionValidation validateSelectedAction(
		List<String> actionValues)
	{
		Objects.requireNonNull(actionValues, "actionValues");
		if (actionValues.size() != 7)
			return SelectionValidation.invalid();
		int selectedCount = 0;
		int selectedIndex = -1;
		CardAction action = null;
		boolean invalid = false;
		for (int i = 0; i < actionValues.size();
			i += 1)
		{
			String value = Objects.requireNonNull(actionValues.get(i), "action value");
			if (value.isBlank())
				continue;
			if (value.equals("U"))
			{
				selectedCount++;
				if (selectedIndex < 0)
				{
					selectedIndex = i;
					action = CardAction.UPDATE;
				}
			}
			else if (value.equals("S"))
			{
				selectedCount++;
				if (selectedIndex < 0)
				{
					selectedIndex = i;
					action = CardAction.DETAIL;
				}
			}
			else invalid = true;
		}
		if (selectedCount > 1)
			return SelectionValidation.multiple();
		if (invalid)
			return SelectionValidation.invalid();
		return selectedIndex < 0 ? SelectionValidation.none()
			: SelectionValidation.selected(selectedIndex, action);
	}
	public record CardKey(String accountId,
		String cardNumber)
	{
		public CardKey
		{
			Objects.requireNonNull(accountId,
				"accountId");
			Objects.requireNonNull(cardNumber,
				"cardNumber");
		}
	}

	public static Optional<CardKey> selectedCardKey(
		SelectionValidation validation,
		List<Optional<CardKey>> selectedRows)
	{
		Objects.requireNonNull(validation, "validation");
		Objects.requireNonNull(selectedRows, "selectedRows");
		if (selectedRows.size() != 7)
			throw new IllegalArgumentException("selected row count mismatch");
		if (validation.selected().isEmpty())
			return Optional.empty();
		int rowIndex = validation.selected().orElseThrow().rowIndex();
		return Objects.requireNonNull(selectedRows.get(rowIndex),
			"selected row").or(() -> {
			throw new IllegalArgumentException(
				"selected row needs adapter-proven context values");
		});
	}

	public record SelectedRoute(CardAction action,
		CardKey key)
	{
		public SelectedRoute
		{
			Objects.requireNonNull(action, "action");
			Objects.requireNonNull(key, "key");
		}
	}

	public static Optional<SelectedRoute> selectedRoute(
		InteractionPlan plan, SelectionValidation validation,
		List<Optional<CardKey>> selectedRows)
	{
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(validation, "validation");
		Objects.requireNonNull(selectedRows, "selectedRows");
		if (plan.action().isEmpty())
			return Optional.empty();
		if (validation.selected().isEmpty())
			throw new IllegalArgumentException("selected route needs validated row");
		CardAction action = validation.selected().orElseThrow().action();
		if (action != plan.action().orElseThrow())
			throw new IllegalArgumentException("selected route action mismatch");
		CardKey key = selectedCardKey(validation, selectedRows)
			.orElseThrow();
		return Optional.of(new SelectedRoute(action, key));
	}

	public interface ActionPort
	{
		void transfer(SelectedRoute route);
	}

	public static void dispatchSelectedRoute(
		Optional<SelectedRoute> route,
		ActionPort port)
	{
		Objects.requireNonNull(route, "route").ifPresent(
			Objects.requireNonNull(port, "port")::transfer);
	}
	public enum BrowseDirection
	{
		FORWARD, BACKWARD
	}

	public enum BrowseOutcome
	{
		FOUND, END, FAILED
	}

	public record BrowsePage(
		BrowseOutcome outcome,
		List<CardRow> rows,
		Optional<CardKey> firstKey,
		Optional<CardKey> lastKey,
		boolean hasNextPage, String message)
	{
		public BrowsePage
		{
			Objects.requireNonNull(outcome, "outcome");
			rows = List.copyOf(
				Objects.requireNonNull(rows, "rows"));
			firstKey = Objects.requireNonNull(firstKey, "firstKey");
			lastKey = Objects.requireNonNull(lastKey, "lastKey");
			Objects.requireNonNull(message, "message");
			if (rows.size() > 7)
				throw new IllegalArgumentException("page exceeds source row bound");
		}
	}

	public interface CardBrowsePort
	{
		BrowsePage browse(
			Optional<CardKey> anchor,
			BrowseDirection direction, int limit);
	}
	public enum GuardedEvent
	{
		PF7, PF8, SELECTED_ENTER
	}

	public enum GuardedRoute
	{
		BROWSE_FORWARD, BROWSE_BACKWARD,
		UPDATE, DETAIL
	}

	public record GuardedRouteRequest(GuardedEvent event,
		boolean firstPage, boolean nextPageExists,
		Optional<CardAction> selectedAction)
	{
		public GuardedRouteRequest
		{
			Objects.requireNonNull(event, "event");
			selectedAction = Objects.requireNonNull(selectedAction,
				"selectedAction");
		}
	}

	public static Optional<GuardedRoute> classifyGuardedRoute(
		GuardedRouteRequest request)
	{
		Objects.requireNonNull(request, "request");
		return switch (request.event())
		{
			case PF7 -> Optional.of(
				request.firstPage() ? GuardedRoute.BROWSE_FORWARD
					: GuardedRoute.BROWSE_BACKWARD);
			case PF8 -> request.nextPageExists()
				? Optional.of(GuardedRoute.BROWSE_FORWARD)
				: Optional.empty();
			case SELECTED_ENTER -> request.selectedAction().map(action ->
				action == CardAction.UPDATE
					? GuardedRoute.UPDATE
					: GuardedRoute.DETAIL);
		};
	}

	public record GuardedExchange(GuardedRoute route,
		Optional<BrowsePage> page,
		Optional<CardKey> selectedKey)
	{
		public GuardedExchange
		{
			Objects.requireNonNull(route, "route");
			page = Objects.requireNonNull(page, "page");
			selectedKey = Objects.requireNonNull(selectedKey, "selectedKey");
			boolean browse = route == GuardedRoute.BROWSE_FORWARD
				|| route == GuardedRoute.BROWSE_BACKWARD;
			if (browse != page.isPresent() || browse == selectedKey.isPresent())
				throw new IllegalArgumentException("route payload mismatch");
		}
	}

	public static Optional<GuardedExchange> resolveGuardedExchange(
		GuardedRouteRequest request,
		Optional<CardKey> selectedKey,
		CardBrowsePort pagePort,
		Optional<CardKey> anchor,
		int limit)
	{
		Objects.requireNonNull(request, "request");
		selectedKey = Objects.requireNonNull(selectedKey, "selectedKey");
		Objects.requireNonNull(pagePort, "pagePort");
		anchor = Objects.requireNonNull(anchor, "anchor");
		if (limit < 1 || limit > 7)
			throw new IllegalArgumentException("source page limit mismatch");
		var classified = classifyGuardedRoute(request);
		if (classified.isEmpty()) return Optional.empty();
		var route = classified.orElseThrow();
		if (route == GuardedRoute.UPDATE
			|| route == GuardedRoute.DETAIL)
		{
			if (selectedKey.isEmpty())
				throw new IllegalArgumentException("selected route needs adapter-proven key");
			return Optional.of(new GuardedExchange(route,
				Optional.empty(), selectedKey));
		}
		var direction = route == GuardedRoute.BROWSE_FORWARD
			? BrowseDirection.FORWARD
			: BrowseDirection.BACKWARD;
		return Optional.of(new GuardedExchange(route,
			Optional.of(Objects.requireNonNull(
				pagePort.browse(anchor, direction, limit), "page")),
			Optional.empty()));
	}
	public enum ContextEvent
	{
		PF3, REENTER, OTHER
	}

	public enum ContextDecision
	{
		MENU_TRANSFER, RESET_LIST_FORWARD
	}

	public record ContextRequest(ContextEvent event,
		boolean callerIsSelf)
	{
		public ContextRequest
		{
			Objects.requireNonNull(event, "event");
		}
	}

	public static Optional<ContextDecision>
		classifyContextReturn(ContextRequest request)
	{
		Objects.requireNonNull(request, "request");
		if (request.event() == ContextEvent.PF3
			&& request.callerIsSelf())
			return Optional.of(
				ContextDecision.MENU_TRANSFER);
		if (!request.callerIsSelf()
			&& (request.event() == ContextEvent.PF3
				|| request.event() == ContextEvent.REENTER))
			return Optional.of(
				ContextDecision.RESET_LIST_FORWARD);
		return Optional.empty();
	}
	public enum TerminalCompletion
	{
		END_INTERACTION
	}

	public record TerminalTextReply(String text,
		TerminalCompletion completion)
	{
		public TerminalTextReply
		{
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(completion, "completion");
		}
	}

	public static TerminalTextReply planPlainTextCompletion(String text)
	{
		return new TerminalTextReply(
			Objects.requireNonNull(text, "text"),
			TerminalCompletion.END_INTERACTION);
	}
	public enum InteractionEvent
	{
		BACK, PREVIOUS_PAGE, NEXT_PAGE,
		SELECTED_ENTER, OTHER
	}

	public enum InteractionOutcome
	{
		RETURN_TO_MENU, REDISPLAY_INPUT_ERROR,
		STAY_ON_FIRST_PAGE, RESET_TO_FIRST_PAGE,
		SHOW_NEXT_PAGE, SHOW_PREVIOUS_PAGE,
		OPEN_DETAIL, OPEN_UPDATE,
		SHOW_DEFAULT_PAGE
	}

	public record InteractionRequest(
		InteractionEvent event, boolean inputError,
		boolean callerIsSelf, boolean reentry, boolean firstPage,
		boolean nextPageExists,
		Optional<CardAction> selectedAction)
	{
		public InteractionRequest
		{
			Objects.requireNonNull(event, "event");
			selectedAction = Objects.requireNonNull(selectedAction,
				"selectedAction");
		}
	}

	public record InteractionPlan(
		InteractionOutcome outcome,
		Optional<CardAction> action)
	{
		public InteractionPlan
		{
			Objects.requireNonNull(outcome, "outcome");
			action = Objects.requireNonNull(action, "action");
			boolean selectedRoute = outcome == InteractionOutcome.OPEN_DETAIL
				|| outcome == InteractionOutcome.OPEN_UPDATE;
			if (selectedRoute != action.isPresent())
				throw new IllegalArgumentException("selected route requires action");
		}
	}

	public static InteractionOutcome classifyInteraction(
		InteractionRequest request)
	{
		Objects.requireNonNull(request, "request");
		if (request.event() == InteractionEvent.BACK
			&& request.callerIsSelf())
			return InteractionOutcome.RETURN_TO_MENU;
		if (request.inputError())
			return InteractionOutcome.REDISPLAY_INPUT_ERROR;
		if (request.event() == InteractionEvent.PREVIOUS_PAGE
			&& request.firstPage())
			return InteractionOutcome.STAY_ON_FIRST_PAGE;
		if (request.event() == InteractionEvent.BACK
			|| (request.reentry() && !request.callerIsSelf()))
			return InteractionOutcome.RESET_TO_FIRST_PAGE;
		if (request.event() == InteractionEvent.NEXT_PAGE
			&& request.nextPageExists())
			return InteractionOutcome.SHOW_NEXT_PAGE;
		if (request.event() == InteractionEvent.PREVIOUS_PAGE
			&& !request.firstPage())
			return InteractionOutcome.SHOW_PREVIOUS_PAGE;
		if (request.event() == InteractionEvent.SELECTED_ENTER
			&& request.callerIsSelf()
			&& request.selectedAction().isPresent())
			return request.selectedAction().orElseThrow()
				== CardAction.DETAIL
					? InteractionOutcome.OPEN_DETAIL
					: InteractionOutcome.OPEN_UPDATE;
		return InteractionOutcome.SHOW_DEFAULT_PAGE;
	}

	public static InteractionPlan planInteraction(
		InteractionRequest request)
	{
		InteractionOutcome outcome = classifyInteraction(request);
		boolean selectedRoute = outcome == InteractionOutcome.OPEN_DETAIL
			|| outcome == InteractionOutcome.OPEN_UPDATE;
		return new InteractionPlan(outcome,
			selectedRoute ? request.selectedAction() : Optional.empty());
	}
	public record InteractionExecution(
		ReceivedInputValidation validation,
		InteractionPlan plan,
		Optional<BrowsePage> page,
		Optional<SelectedRoute> selectedRoute)
	{
		public InteractionExecution
		{
			Objects.requireNonNull(validation, "validation");
			Objects.requireNonNull(plan, "plan");
			page = Objects.requireNonNull(page, "page");
			selectedRoute = Objects.requireNonNull(selectedRoute,
				"selected route");
			if (plan.action().isPresent() != selectedRoute.isPresent())
				throw new IllegalArgumentException("selected plan route mismatch");
			if (page.isPresent() && selectedRoute.isPresent())
				throw new IllegalArgumentException("execution has one result kind");
		}
	}

	public static InteractionExecution executeInteraction(
		ReceivedInput input, InteractionRequest request,
		List<Optional<CardKey>> selectedRows,
		CardBrowsePort pagePort, ActionPort actionPort,
		Optional<CardKey> anchor, int limit)
	{
		var received = Objects.requireNonNull(input, "input");
		var original = Objects.requireNonNull(request, "request");
		Objects.requireNonNull(selectedRows, "selected rows");
		Objects.requireNonNull(pagePort, "page port");
		Objects.requireNonNull(actionPort, "action port");
		anchor = Objects.requireNonNull(anchor, "anchor");
		if (limit < 1 || limit > 7)
			throw new IllegalArgumentException("source page limit mismatch");
		var validation = validateReceivedInput(received);
		var normalized = new InteractionRequest(original.event(),
			original.inputError() || !validation.valid(),
			original.callerIsSelf(), original.reentry(), original.firstPage(),
			original.nextPageExists(), validation.selection().selected()
				.map(selected -> selected.action()));
		var plan = planInteraction(normalized);
		if (!validation.valid() || plan.outcome()
			== InteractionOutcome.RETURN_TO_MENU
			|| plan.outcome() == InteractionOutcome.REDISPLAY_INPUT_ERROR)
			return new InteractionExecution(validation, plan,
				Optional.empty(), Optional.empty());
		var route = selectedRoute(plan, validation.selection(), selectedRows);
		if (route.isPresent())
		{
			dispatchSelectedRoute(route, actionPort);
			return new InteractionExecution(validation, plan,
				Optional.empty(), route);
		}
		var direction = plan.outcome()
			== InteractionOutcome.SHOW_PREVIOUS_PAGE
			? BrowseDirection.BACKWARD
			: BrowseDirection.FORWARD;
		var browseAnchor = plan.outcome()
			== InteractionOutcome.RESET_TO_FIRST_PAGE
			? Optional.<CardKey>empty() : anchor;
		return new InteractionExecution(validation, plan,
			Optional.of(Objects.requireNonNull(
				pagePort.browse(browseAnchor, direction, limit),
				"page")), Optional.empty());
	}

	public interface InteractionContextPort
	{
		InteractionRequest request();
		List<Optional<CardKey>> selectedRows();
		Optional<CardKey> anchor();
		int pageLimit();
	}

	public interface InteractionCompletionPort
	{
		void complete(InteractionExecution execution);
	}

	private final ScreenPort screen;
	private final InteractionContextPort context;
	private final CardBrowsePort pages;
	private final ActionPort actions;
	private final InteractionCompletionPort completion;

	public CardListController(ScreenPort screen,
		InteractionContextPort context, CardBrowsePort pages,
		ActionPort actions, InteractionCompletionPort completion)
	{
		this.screen = Objects.requireNonNull(screen, "screen");
		this.context = Objects.requireNonNull(context, "context");
		this.pages = Objects.requireNonNull(pages, "pages");
		this.actions = Objects.requireNonNull(actions, "actions");
		this.completion = Objects.requireNonNull(completion,
			"completion");
	}

	public InteractionExecution handle()
	{
		InteractionExecution execution = executeInteraction(
			Objects.requireNonNull(screen.receive(),
				"received input"),
			Objects.requireNonNull(context.request(),
				"interaction request"),
			List.copyOf(Objects.requireNonNull(
				context.selectedRows(), "selected rows")),
			pages, actions,
			Objects.requireNonNull(context.anchor(),
				"browse anchor"), context.pageLimit());
		completion.complete(execution);
		return execution;
	}
}
