package com.cardemo.user;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AdminMenuController
{
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

	public boolean acceptWsVariablesWsOption(String candidate, BigDecimal maximum, AdminMenuFeedbackPort feedbackPort)
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
		return List.copyOf(rows.subList(0, Math.min(rows.size(), 10)));
	}

	public void showMenuOptions(List<MenuOption> options, int declaredCount, AdminMenuOptionsPort optionsPort)
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

	public MenuSelection readMenuSelection(AdminMenuInputPort<String> port)
	{
		return new MenuSelection(normalizeMenuSelection(
			Objects.requireNonNull(port, "port").read()));
	}

	public boolean acceptMenuSelection(MenuSelection selection,
		BigDecimal maximum, AdminMenuFeedbackPort feedbackPort)
	{
		Optional<String> rejection = validateMenuSelection(
			Objects.requireNonNull(selection, "selection").choice(),
			Objects.requireNonNull(maximum, "maximum"));
		rejection.ifPresent(Objects.requireNonNull(feedbackPort, "feedbackPort")::showError);
		return rejection.isEmpty();
	}

	public record ProgramRoute(String targetProgram)
	{
		public ProgramRoute
		{
			Objects.requireNonNull(targetProgram, "targetProgram");
		}
	}

	public interface ProgramCatalog
	{
		Optional<ProgramRoute> routeFor(MenuSelection selection);
	}

	public Optional<ProgramRoute> routeMenuSelection(
		MenuSelection selection, ProgramCatalog catalog)
	{
		return Objects.requireNonNull(catalog, "catalog")
			.routeFor(Objects.requireNonNull(selection, "selection"));
	}

	private record ProgramCatalogEntry(String choice, String label, ProgramRoute route) { }

	private static final List<ProgramCatalogEntry> PROGRAM_CATALOG = List.of(
		new ProgramCatalogEntry("1", "User List (Security)",
			new ProgramRoute("COUSR00C")),
		new ProgramCatalogEntry("2", "User Add (Security)",
			new ProgramRoute("COUSR01C")),
		new ProgramCatalogEntry("3", "User Update (Security)",
			new ProgramRoute("COUSR02C")),
		new ProgramCatalogEntry("4", "User Delete (Security)",
			new ProgramRoute("COUSR03C")),
		new ProgramCatalogEntry("5", "Transaction Type List/Update (Db2)",
			new ProgramRoute("COTRTLIC")),
		new ProgramCatalogEntry("6", "Transaction Type Maintenance (Db2)",
			new ProgramRoute("COTRTUPC"))
	);

	public static ProgramCatalog initializedProgramCatalog()
	{
		return selection ->
		{
			String choice = Objects.requireNonNull(selection, "selection").choice();
			if (!choice.matches("[0-9]+")) return Optional.empty();
			BigDecimal selected = new BigDecimal(choice);
			for (ProgramCatalogEntry entry : PROGRAM_CATALOG)
			{
				if (selected.compareTo(new BigDecimal(entry.choice())) == 0)
					return Optional.of(entry.route());
			}
			return Optional.empty();
		};
	}

	public static List<MenuOption> catalogMenuOptions()
	{
		return PROGRAM_CATALOG.stream()
			.map(entry -> new MenuOption(entry.choice(), entry.label()))
			.toList();
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

	public void presentMenuView(MenuView view, AdminMenuPresentationPort<PlacedText> port)
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
	public void handleConditionFeedback(AdminMenuFeedbackPort feedbackPort)
	{
		feedbackPort.showError("This option is not installed ...");
	}

	public void handle()
	{
		throw new UnsupportedOperationException(
			"generated controller requires further Style refinement");
	}
}
