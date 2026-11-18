package com.cardemo.exportimport.importing;

import java.util.Objects;
import java.util.Optional;

public final class CustomerImportValueClassifier
{
	private CustomerImportValueClassifier() { }
	public enum RecordKind
	{
		CUSTOMER,
		ACCOUNT,
		CARD_XREF,
		TRAN,
		CARD
	}

	public static Optional<RecordKind> classify(String sourceType)
	{
		Objects.requireNonNull(sourceType, "sourceType");
		return switch (sourceType)
		{
		case "C" -> Optional.of(
			RecordKind.CUSTOMER);
		case "A" -> Optional.of(
			RecordKind.ACCOUNT);
		case "X" -> Optional.of(
			RecordKind.CARD_XREF);
		case "T" -> Optional.of(
			RecordKind.TRAN);
		case "D" -> Optional.of(
			RecordKind.CARD);
		default -> Optional.empty();
		};
	}
}
