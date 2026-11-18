package com.cardemo.auth;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MainMenuController
{
	private long cdemoPgmContext;
	private enum WsErrFlgState { ON, OFF }
	private WsErrFlgState wsErrFlg = WsErrFlgState.OFF;
	private static Optional<String> validateMenuSelection(String candidate, BigDecimal maximum)
	{
		if (candidate == null || candidate.length() > 2 || !candidate.matches("[0-9]+"))
			return Optional.of("Please enter a valid option number...");
		BigDecimal number = new BigDecimal(candidate);
		return number.signum() > 0 && number.compareTo(maximum) <= 0
			? Optional.empty()
			: Optional.of("Please enter a valid option number...");
	}

	public boolean acceptWsVariablesWsOption(String candidate, BigDecimal maximum, MainMenuFeedbackPort feedbackPort)
	{
		Optional<String> feedback = validateMenuSelection(candidate, maximum);
		if (feedback.isPresent())
		{
			wsErrFlg = WsErrFlgState.ON;
			feedbackPort.showError(feedback.get());
		}
		return wsErrFlg == WsErrFlgState.OFF;
	}

	public static String normalizeBlankAsZeroOption(String raw)
	{
		Objects.requireNonNull(raw, "raw");
		char blank = " ".charAt(0);
		char replacement = "0".charAt(0);
		int significant = raw.length();
		while (significant > 1 && raw.charAt(significant - 1) == blank)
			significant--;
		char[] normalized = new char[2];
		Arrays.fill(normalized, blank);
		int copied = Math.min(significant, Math.min(raw.length(), normalized.length));
		raw.getChars(0, copied, normalized, 0);
		for (int i = 0; i < normalized.length; i++)
		{
			if (normalized[i] == blank)
				normalized[i] = replacement;
		}
		return new String(normalized);
	}

	public static String formatOptionLabel(String number, String label)
	{
		return number + ". " + label;
	}

	public record MenuOption(String number, String name) { }

	public static List<String> menuRows(List<MenuOption> options, int declaredCount)
	{
		Objects.requireNonNull(options, "options");
		if (declaredCount < 0)
			throw new IllegalArgumentException("declaredCount must be nonnegative");
		int count = Math.min(declaredCount, options.size());
		List<String> rows = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			MenuOption option = options.get(i);
			rows.add(formatOptionLabel(option.number(), option.name()));
		}
		return List.copyOf(rows);
	}

	public static List<String> visibleMenuRows(List<String> rows)
	{
		Objects.requireNonNull(rows, "rows");
		return List.copyOf(rows.subList(0, Math.min(rows.size(), 12)));
	}

	public void showMenuOptions(List<MenuOption> options, int declaredCount, MainMenuOptionsPort optionsPort)
	{
		Objects.requireNonNull(optionsPort, "optionsPort");
		optionsPort.showOptions(visibleMenuRows(menuRows(options, declaredCount)));
	}

	public record MenuView(List<String> headerValues,
		List<String> optionRows, String message)
	{
		public MenuView
		{
			if (headerValues.size() != 6)
				throw new IllegalArgumentException("headerValues must match source-declared fields");
			headerValues = List.copyOf(headerValues);
			optionRows = List.copyOf(optionRows);
			Objects.requireNonNull(message, "message");
		}
	}

	public static MenuView assembleMenuView(List<String> headerValues,
		List<MenuOption> options, int declaredCount, String message)
	{
		return new MenuView(headerValues, visibleMenuRows(menuRows(options, declaredCount)), message);
	}

	public record ScreenField(String name, int row, int column, int length)
	{
		public ScreenField
		{
			Objects.requireNonNull(name, "name");
			if (row < 1 || column < 1 || length < 1)
				throw new IllegalArgumentException("screen field geometry must be positive");
		}
	}

	public static List<ScreenField> screenLayout()
	{
		return List.of(
			new ScreenField("TRNNAME", 1, 7, 4),
			new ScreenField("TITLE01", 1, 21, 40),
			new ScreenField("CURDATE", 1, 71, 8),
			new ScreenField("PGMNAME", 2, 7, 8),
			new ScreenField("TITLE02", 2, 21, 40),
			new ScreenField("CURTIME", 2, 71, 8),
			new ScreenField("OPTN001", 6, 20, 40),
			new ScreenField("OPTN002", 7, 20, 40),
			new ScreenField("OPTN003", 8, 20, 40),
			new ScreenField("OPTN004", 9, 20, 40),
			new ScreenField("OPTN005", 10, 20, 40),
			new ScreenField("OPTN006", 11, 20, 40),
			new ScreenField("OPTN007", 12, 20, 40),
			new ScreenField("OPTN008", 13, 20, 40),
			new ScreenField("OPTN009", 14, 20, 40),
			new ScreenField("OPTN010", 15, 20, 40),
			new ScreenField("OPTN011", 16, 20, 40),
			new ScreenField("OPTN012", 17, 20, 40),
			new ScreenField("OPTION", 20, 41, 2),
			new ScreenField("ERRMSG", 23, 1, 78)
		);
	}

	public static String normalizeMenuSelection(String raw)
	{
		return normalizeBlankAsZeroOption(raw);
	}

	public record MenuSelection(String choice)
	{
		public MenuSelection
		{
			Objects.requireNonNull(choice, "choice");
		}
	}

	public MenuSelection readMenuSelection(MainMenuInputPort<String> port)
	{
		return new MenuSelection(normalizeMenuSelection(
			Objects.requireNonNull(port, "port").read()));
	}

	public boolean acceptMenuSelection(MenuSelection selection,
		BigDecimal maximum, MainMenuFeedbackPort feedbackPort)
	{
		Optional<String> rejection = validateMenuSelection(
			Objects.requireNonNull(selection, "selection").choice(),
			Objects.requireNonNull(maximum, "maximum"));
		rejection.ifPresent(Objects.requireNonNull(feedbackPort, "feedbackPort")::showError);
		return rejection.isEmpty();
	}

	public enum MenuAccess { STANDARD, PRIVILEGED }
	public enum TargetOutcome { PRESENT, ROUTE, SHOW_ERROR }

	public interface CapabilityPort
	{
		boolean available(String destination);
	}

	public record TargetDecision(TargetOutcome outcome,
		Optional<String> destination, String message)
	{
		public TargetDecision
		{
			Objects.requireNonNull(outcome, "outcome");
			Objects.requireNonNull(destination, "destination");
			Objects.requireNonNull(message, "message");
		}
	}

	private record TargetCatalogEntry(String choice, String label,
		String destination, boolean privilegedRequired,
		boolean capabilityChecked, boolean placeholder) { }

	private static final List<TargetCatalogEntry> TARGET_CATALOG = List.of(
		new TargetCatalogEntry("1", "Account View",
			"COACTVWC", false,
			false, false),
		new TargetCatalogEntry("2", "Account Update",
			"COACTUPC", false,
			false, false),
		new TargetCatalogEntry("3", "Credit Card List",
			"COCRDLIC", false,
			false, false),
		new TargetCatalogEntry("4", "Credit Card View",
			"COCRDSLC", false,
			false, false),
		new TargetCatalogEntry("5", "Credit Card Update",
			"COCRDUPC", false,
			false, false),
		new TargetCatalogEntry("6", "Transaction List",
			"COTRN00C", false,
			false, false),
		new TargetCatalogEntry("7", "Transaction View",
			"COTRN01C", false,
			false, false),
		new TargetCatalogEntry("8", "Transaction Add",
			"COTRN02C", false,
			false, false),
		new TargetCatalogEntry("9", "Transaction Reports",
			"CORPT00C", false,
			false, false),
		new TargetCatalogEntry("10", "Bill Payment",
			"COBIL00C", false,
			false, false),
		new TargetCatalogEntry("11", "Pending Authorization View",
			"COPAUS0C", false,
			true, false)
	);

	public static TargetDecision decideTarget(String selection, MenuAccess access,
		CapabilityPort capabilities)
	{
		Objects.requireNonNull(selection, "selection");
		Objects.requireNonNull(access, "access");
		Objects.requireNonNull(capabilities, "capabilities");
		TargetCatalogEntry selected = TARGET_CATALOG.stream()
			.filter(entry -> entry.choice().equals(selection))
			.findFirst().orElse(null);
		if (selected == null)
			return new TargetDecision(TargetOutcome.SHOW_ERROR,
				Optional.empty(), "");
		if (selected.privilegedRequired() && access != MenuAccess.PRIVILEGED)
			return new TargetDecision(TargetOutcome.SHOW_ERROR,
				Optional.empty(), "No access - Admin Only option... ");
		if (selected.placeholder())
			return new TargetDecision(TargetOutcome.SHOW_ERROR,
				Optional.empty(), "This option "
					+ selected.label() + "is coming soon ...");
		if (selected.capabilityChecked()
			&& !capabilities.available(selected.destination()))
			return new TargetDecision(TargetOutcome.SHOW_ERROR,
				Optional.empty(), "This option "
					+ selected.label() + " is not installed...");
		return new TargetDecision(TargetOutcome.ROUTE,
			Optional.of(selected.destination()), "");
	}

	public enum MenuEvent { ENTER, BACK, OTHER }

	public static TargetDecision decideMenuEvent(MenuEvent event,
		String selection, MenuAccess access, CapabilityPort capabilities)
	{
		return switch (Objects.requireNonNull(event, "event"))
		{
			case ENTER -> decideTarget(selection, access, capabilities);
			case BACK -> new TargetDecision(TargetOutcome.ROUTE,
				Optional.of("COSGN00C"), "");
			case OTHER -> new TargetDecision(TargetOutcome.SHOW_ERROR,
				Optional.empty(), "Invalid key pressed. Please see below...");
		};
	}

	public enum MenuVisit { INITIAL_SIGN_ON, FIRST_PRESENTATION, RECEIVED_EVENT }

	public record MenuRequest(MenuVisit visit, MenuEvent event,
		String selection, MenuAccess access)
	{
		public MenuRequest
		{
			Objects.requireNonNull(visit, "visit");
			Objects.requireNonNull(event, "event");
			Objects.requireNonNull(selection, "selection");
			Objects.requireNonNull(access, "access");
		}
	}

	public static TargetDecision handleMenuRequest(MenuRequest request,
		CapabilityPort capabilities)
	{
		Objects.requireNonNull(request, "request");
		return switch (request.visit())
		{
			case INITIAL_SIGN_ON -> new TargetDecision(TargetOutcome.ROUTE,
				Optional.of("COSGN00C"), "");
			case FIRST_PRESENTATION -> new TargetDecision(TargetOutcome.PRESENT,
				Optional.empty(), "");
			case RECEIVED_EVENT -> decideMenuEvent(request.event(),
				request.selection(), request.access(), capabilities);
		};
	}

	public record MenuResponse(TargetDecision decision,
		List<PlacedText> presentation)
	{
		public MenuResponse
		{
			Objects.requireNonNull(decision, "decision");
			presentation = List.copyOf(presentation);
		}
	}

	public static MenuResponse renderMenuRequest(MenuRequest request,
		MenuHeader header, CapabilityPort capabilities)
	{
		TargetDecision decision = handleMenuRequest(request, capabilities);
		List<PlacedText> presentation = switch (decision.outcome())
		{
			case ROUTE -> List.of();
			case PRESENT -> placeMenuView(assembleMenuView(header,
				TARGET_CATALOG.stream().map(entry -> new MenuOption(entry.choice(), entry.label())).toList(),
				11, ""));
			case SHOW_ERROR -> placeMenuView(assembleMenuView(header,
				TARGET_CATALOG.stream().map(entry -> new MenuOption(entry.choice(), entry.label())).toList(),
				11, decision.message()));
		};
		return new MenuResponse(decision, presentation);
	}

	public enum ScreenValueRole { HEADER, OPTION, MESSAGE }

	public record ScreenValueField(ScreenField field, ScreenValueRole role, int index)
	{
		public ScreenValueField
		{
			Objects.requireNonNull(field, "field");
			Objects.requireNonNull(role, "role");
			if (index < 0) throw new IllegalArgumentException("screen value index must be nonnegative");
		}
	}

	public record PlacedText(ScreenField field, String value)
	{
		public PlacedText
		{
			Objects.requireNonNull(field, "field");
			Objects.requireNonNull(value, "value");
		}
	}

	private static final List<ScreenValueField> SCREEN_VALUE_FIELDS = List.of(
		new ScreenValueField(new ScreenField("TITLE01", 1, 21, 40),
			ScreenValueRole.HEADER, 0),
		new ScreenValueField(new ScreenField("TITLE02", 2, 21, 40),
			ScreenValueRole.HEADER, 1),
		new ScreenValueField(new ScreenField("TRNNAME", 1, 7, 4),
			ScreenValueRole.HEADER, 2),
		new ScreenValueField(new ScreenField("PGMNAME", 2, 7, 8),
			ScreenValueRole.HEADER, 3),
		new ScreenValueField(new ScreenField("CURDATE", 1, 71, 8),
			ScreenValueRole.HEADER, 4),
		new ScreenValueField(new ScreenField("CURTIME", 2, 71, 8),
			ScreenValueRole.HEADER, 5),
		new ScreenValueField(new ScreenField("OPTN001", 6, 20, 40),
			ScreenValueRole.OPTION, 0),
		new ScreenValueField(new ScreenField("OPTN002", 7, 20, 40),
			ScreenValueRole.OPTION, 1),
		new ScreenValueField(new ScreenField("OPTN003", 8, 20, 40),
			ScreenValueRole.OPTION, 2),
		new ScreenValueField(new ScreenField("OPTN004", 9, 20, 40),
			ScreenValueRole.OPTION, 3),
		new ScreenValueField(new ScreenField("OPTN005", 10, 20, 40),
			ScreenValueRole.OPTION, 4),
		new ScreenValueField(new ScreenField("OPTN006", 11, 20, 40),
			ScreenValueRole.OPTION, 5),
		new ScreenValueField(new ScreenField("OPTN007", 12, 20, 40),
			ScreenValueRole.OPTION, 6),
		new ScreenValueField(new ScreenField("OPTN008", 13, 20, 40),
			ScreenValueRole.OPTION, 7),
		new ScreenValueField(new ScreenField("OPTN009", 14, 20, 40),
			ScreenValueRole.OPTION, 8),
		new ScreenValueField(new ScreenField("OPTN010", 15, 20, 40),
			ScreenValueRole.OPTION, 9),
		new ScreenValueField(new ScreenField("OPTN011", 16, 20, 40),
			ScreenValueRole.OPTION, 10),
		new ScreenValueField(new ScreenField("OPTN012", 17, 20, 40),
			ScreenValueRole.OPTION, 11),
		new ScreenValueField(new ScreenField("ERRMSG", 23, 1, 78),
			ScreenValueRole.MESSAGE, 0)
	);

	public static List<ScreenValueField> screenValueFields()
	{
		return SCREEN_VALUE_FIELDS;
	}

	public static List<PlacedText> placeMenuView(MenuView view)
	{
		Objects.requireNonNull(view, "view");
		long optionCapacity = SCREEN_VALUE_FIELDS.stream()
			.filter(field -> field.role() == ScreenValueRole.OPTION).count();
		if (view.optionRows().size() > optionCapacity)
			throw new IllegalArgumentException("option rows exceed screen capacity");
		List<PlacedText> placements = new ArrayList<>(SCREEN_VALUE_FIELDS.size());
		for (ScreenValueField target : SCREEN_VALUE_FIELDS)
		{
			String value = switch (target.role())
			{
				case HEADER -> view.headerValues().get(target.index());
				case OPTION -> target.index() < view.optionRows().size()
					? view.optionRows().get(target.index()) : "";
				case MESSAGE -> view.message();
			};
			placements.add(new PlacedText(target.field(), value));
		}
		return List.copyOf(placements);
	}

	public void presentMenuView(MenuView view, MainMenuPresentationPort<PlacedText> port)
	{
		Objects.requireNonNull(port, "port").present(placeMenuView(view));
	}

	public record MenuHeader(String primaryTitle, String secondaryTitle,
		String transactionId, String programId, String displayDate, String displayTime)
	{
		public List<String> values()
		{
			return List.of(primaryTitle, secondaryTitle,
				transactionId, programId, displayDate, displayTime);
		}
	}

	public static MenuHeader menuHeader(String primaryTitle, String secondaryTitle,
		String transactionId, String programId, Clock clock)
	{
		LocalDateTime now = LocalDateTime.now(
			Objects.requireNonNull(clock, "clock"));
		return new MenuHeader(primaryTitle, secondaryTitle, transactionId, programId,
			now.format(DateTimeFormatter.ofPattern("MM/dd/yy")),
			now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
	}

	public static MenuView assembleMenuView(MenuHeader header,
		List<MenuOption> options, int declaredCount, String message)
	{
		return assembleMenuView(Objects.requireNonNull(header, "header").values(),
			options, declaredCount, message);
	}

	public static String selectNextProgram(String currentProgram)
	{
		if (currentProgram == null || currentProgram.isEmpty()
			|| currentProgram.equals("\u0000".repeat(8))
			|| currentProgram.chars().allMatch(ch -> ch == ' '))
			return "COSGN00C";
		return currentProgram;
	}
}
