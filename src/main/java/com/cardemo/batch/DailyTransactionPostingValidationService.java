package com.cardemo.batch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public final class DailyTransactionPostingValidationService
{
	private DailyTransactionPostingValidationService() { }
	public enum ValidationIssue { NONE, OVER_LIMIT, EXPIRED }
	public record ValidationResult(ValidationIssue issue,
		BigDecimal candidateBalance, String message)
	{
		public ValidationResult
		{
			Objects.requireNonNull(issue, "issue");
			Objects.requireNonNull(candidateBalance, "candidateBalance");
			Objects.requireNonNull(message, "message");
		}
	}

	public static ValidationResult validate(
		DailyTransactionPostingAccountFileRepository.AccountSnapshot account,
		BigDecimal transactionAmount, String originTimestamp)
	{
		Objects.requireNonNull(account, "account");
		Objects.requireNonNull(transactionAmount, "transactionAmount");
		Objects.requireNonNull(originTimestamp, "originTimestamp");
		if (originTimestamp.length() < 10)
			throw new IllegalArgumentException("origin date slice");
		BigDecimal candidate =
			Objects.requireNonNull(account.acctCurrCycCredit(), "cycle credit")
				.subtract(Objects.requireNonNull(account.acctCurrCycDebit(), "cycle debit"))
				.add(transactionAmount);
		ValidationIssue issue = ValidationIssue.NONE;
		String message = "";
		if (Objects.requireNonNull(account.acctCreditLimit(),
			"credit limit").compareTo(candidate) < 0)
		{
			issue = ValidationIssue.OVER_LIMIT;
			message = "OVERLIMIT TRANSACTION";
		}
		if (Objects.requireNonNull(account.acctExpiraionDate(),
			"expiration date").isBefore(originDate(originTimestamp)))
		{
			issue = ValidationIssue.EXPIRED;
			message = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
		}
		return new ValidationResult(issue, candidate, message);
	}

	// The expiry is a typed date; the origin timestamp arrives as the source's
	// text, and the source compares only its date part (positions
	// 0-10, ISO yyyy-MM-dd).
	private static LocalDate originDate(String originTimestamp)
	{
		return LocalDate.parse(originTimestamp.substring(
			0, 10));
	}
	public enum TransactionIssue
	{
		NONE, XREF_FILE_KEY_NOT_FOUND, ACCOUNT_FILE_KEY_NOT_FOUND, OVER_LIMIT, EXPIRED
	}
	public record TransactionValidationResult(TransactionIssue issue,
		Optional<DailyTransactionPostingAccountFileRepository.AccountSnapshot> account,
		BigDecimal candidateBalance, String message)
	{
		public TransactionValidationResult
		{
			Objects.requireNonNull(issue, "issue");
			account = Objects.requireNonNull(account, "account");
			Objects.requireNonNull(candidateBalance, "candidateBalance");
			Objects.requireNonNull(message, "message");
		}
	}

	public static TransactionValidationResult validateTransaction(
		DailyTransactionPostingXrefFileRepository xrefFilePort,
		DailyTransactionPostingAccountFileRepository accountFilePort,
		String transactionCard,
		BigDecimal transactionAmount, String originTimestamp)
	{
		Objects.requireNonNull(xrefFilePort, "xrefFilePort");
		Objects.requireNonNull(accountFilePort, "accountFilePort");
		Objects.requireNonNull(transactionCard, "transactionCard");
		Objects.requireNonNull(transactionAmount, "transactionAmount");
		Objects.requireNonNull(originTimestamp, "originTimestamp");
		var xrefFileLookup = xrefFilePort.findByKey(transactionCard);
		if (xrefFileLookup.outcome() != DailyTransactionPostingXrefFileRepository.LookupOutcome.FOUND)
			return new TransactionValidationResult(TransactionIssue.XREF_FILE_KEY_NOT_FOUND,
				Optional.empty(), BigDecimal.ZERO,
				"INVALID CARD NUMBER FOUND");
		var xrefFileValue = xrefFileLookup.value().orElseThrow();
		var accountFileLookup = accountFilePort.findByKey(xrefFileValue.xrefAcctId());
		if (accountFileLookup.outcome() != DailyTransactionPostingAccountFileRepository.LookupOutcome.FOUND)
			return new TransactionValidationResult(TransactionIssue.ACCOUNT_FILE_KEY_NOT_FOUND,
				Optional.empty(), BigDecimal.ZERO,
				"ACCOUNT RECORD NOT FOUND");
		var account = accountFileLookup.value().orElseThrow();
		var checked = validate(account, transactionAmount, originTimestamp);
		TransactionIssue issue = switch (checked.issue())
		{
			case NONE -> TransactionIssue.NONE;
			case OVER_LIMIT -> TransactionIssue.OVER_LIMIT;
			case EXPIRED -> TransactionIssue.EXPIRED;
		};
		return new TransactionValidationResult(issue,
			Optional.of(account), checked.candidateBalance(), checked.message());
	}
}
