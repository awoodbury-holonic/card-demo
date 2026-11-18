package com.cardemo.account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public final class AccountViewController
{
	private final JdbcTemplate browseDatabase;
	private final CardCrossReferenceWsCardRidAcctIdXLookupPort crossReferences;
	private final AccountWsCardRidAcctIdXLookupPort accounts;
	private final CustomerWsCardRidCustIdXLookupPort customers;
	private final CardCrossReferenceNextKeyPort customerKeys;

	public AccountViewController(
		CardCrossReferenceWsCardRidAcctIdXLookupPort crossReferences,
		AccountWsCardRidAcctIdXLookupPort accounts,
		CustomerWsCardRidCustIdXLookupPort customers,
		CardCrossReferenceNextKeyPort customerKeys,
		JdbcTemplate browseDatabase)
	{
		this.crossReferences = Objects.requireNonNull(crossReferences);
		this.accounts = Objects.requireNonNull(accounts);
		this.customers = Objects.requireNonNull(customers);
		this.customerKeys = Objects.requireNonNull(customerKeys);
		this.browseDatabase = Objects.requireNonNull(browseDatabase);
	}

	public record AccountInquiryResponse(AccountDisplay account,
		CustomerDisplay customer)
	{
		public AccountInquiryResponse
		{
			Objects.requireNonNull(account, "account");
			Objects.requireNonNull(customer, "customer");
		}
	}

	public enum BrowseDirection { FORWARD, BACKWARD }
	public enum BrowseAnchor { INCLUDE_ANCHOR, EXCLUDE_ANCHOR }

	public record AccountListRow(String accountId, String status,
		BigDecimal currentBalance, BigDecimal creditLimit) { }

	public record AccountListPage(List<AccountListRow> rows, String firstKey,
		String lastKey, boolean hasPrevious, boolean hasNext) { }

	@GetMapping
	public ResponseEntity<AccountListPage> listAccounts(
		@RequestParam(name = "startKey", defaultValue = "") String startKey,
		@RequestParam(name = "direction", defaultValue = "FORWARD") BrowseDirection direction,
		@RequestParam(name = "anchorMode", defaultValue = "INCLUDE_ANCHOR") BrowseAnchor anchorMode)
	{
		String key = startKey.trim();
		if (!key.isEmpty() && (key.length() > 11
			|| !key.chars().allMatch(ch -> ch >= '0' && ch <= '9')))
			return ResponseEntity.badRequest().build();
		boolean backward = direction == BrowseDirection.BACKWARD;
		long anchor = key.isEmpty() ? (backward ? 99999999999L : 0L)
			: Long.parseLong(key);
		String comparison = backward ? "<" : ">";
		if (anchorMode == BrowseAnchor.INCLUDE_ANCHOR) comparison += "=";
		// Only validated enum choices select SQL operators; the key is bound.
		String sql = "SELECT ACCT_ID, ACCT_ACTIVE_STATUS, "
			+ "ACCT_CURR_BAL, ACCT_CREDIT_LIMIT "
			+ "FROM ACCTDAT WHERE ACCT_ID " + comparison
			+ " ? ORDER BY ACCT_ID " + (backward ? "DESC" : "ASC")
			+ " FETCH FIRST 10 ROWS ONLY";
		List<AccountListRow> rows = new ArrayList<>(browseDatabase.query(sql,
			(row, index) -> new AccountListRow(
				String.format(Locale.ROOT, "%011d", row.getLong(1)),
				row.getString(2).stripTrailing(), row.getBigDecimal(3), row.getBigDecimal(4)), anchor));
		if (backward) Collections.reverse(rows);
		if (rows.isEmpty())
			return ResponseEntity.ok(new AccountListPage(List.of(), null, null, false, false));
		String firstKey = rows.get(0).accountId();
		String lastKey = rows.get(rows.size() - 1).accountId();
		boolean previous = !browseDatabase.queryForList(
			"SELECT ACCT_ID FROM ACCTDAT WHERE "
			+ "ACCT_ID < ? FETCH FIRST 1 ROW ONLY", Long.class,
			Long.parseLong(firstKey)).isEmpty();
		boolean next = !browseDatabase.queryForList(
			"SELECT ACCT_ID FROM ACCTDAT WHERE "
			+ "ACCT_ID > ? FETCH FIRST 1 ROW ONLY", Long.class,
			Long.parseLong(lastKey)).isEmpty();
		return ResponseEntity.ok(new AccountListPage(List.copyOf(rows), firstKey, lastKey, previous, next));
	}

	@GetMapping("/{accountId}")
	public ResponseEntity<AccountInquiryResponse> findAccount(
		@PathVariable("accountId") String accountId)
	{
		NumericKeyValidation validation = validateNumericKey(
			Optional.ofNullable(accountId));
		if (validation.reason() != NumericKeyValidationReason.VALID)
			return ResponseEntity.badRequest().build();
		String key = validation.key().orElseThrow().digits();
		CardCrossReferenceInquiry inquiry = inquireCardCrossReference(key,
			crossReferences, accounts, customers, customerKeys);
		if (inquiry.cardCrossReference().outcome()
			== CardCrossReferenceWsCardRidAcctIdXLookupOutcome.FAILED
			|| inquiry.account().map(AccountWsCardRidAcctIdXLookupResult::outcome)
				.orElse(AccountWsCardRidAcctIdXLookupOutcome.NOT_FOUND)
				== AccountWsCardRidAcctIdXLookupOutcome.FAILED
			|| inquiry.customer().map(CustomerWsCardRidCustIdXLookupResult::outcome)
				.orElse(CustomerWsCardRidCustIdXLookupOutcome.NOT_FOUND)
				== CustomerWsCardRidCustIdXLookupOutcome.FAILED)
			return ResponseEntity.internalServerError().build();
		CardCrossReferencePresentation presentation = presentCardCrossReference(true,
			Optional.of(key), inquiry, "", Optional.empty());
		if (presentation.account().isEmpty() || presentation.customer().isEmpty())
			return ResponseEntity.notFound().build();
		return ResponseEntity.ok(new AccountInquiryResponse(
			presentation.account().orElseThrow(), presentation.customer().orElseThrow()));
	}

	public enum NumericKeyValidationReason
	{
		REQUIRED, NON_NUMERIC_OR_ZERO, VALID
	}

	public record NumericKey(String digits)
	{
		public NumericKey
		{
			Objects.requireNonNull(digits, "digits");
			if (digits.length() != 11
				|| !digits.chars().allMatch(ch -> ch >= '0' && ch <= '9')
				|| digits.chars().allMatch(ch -> ch == '0'))
				throw new IllegalArgumentException("numeric key invariant");
		}
	}

	public record NumericKeyValidation(
		NumericKeyValidationReason reason,
		Optional<NumericKey> key,
		String feedback)
	{
		public NumericKeyValidation
		{
			Objects.requireNonNull(reason, "reason");
			key = Objects.requireNonNull(key, "key");
			Objects.requireNonNull(feedback, "feedback");
			if ((reason == NumericKeyValidationReason.VALID) != key.isPresent())
				throw new IllegalArgumentException("valid key and reason disagree");
		}
	}

	public static NumericKeyValidation validateNumericKey(
		Optional<String> normalizedInput)
	{
		Optional<String> input = Objects.requireNonNull(
			normalizedInput, "normalizedInput").filter(text -> !text.isBlank());
		if (input.isEmpty())
			return new NumericKeyValidation(NumericKeyValidationReason.REQUIRED,
				Optional.empty(), "Account number not provided");
		String text = input.orElseThrow();
		if (text.length() != 11 || !text.chars().allMatch(ch -> ch >= '0' && ch <= '9')
			|| text.chars().allMatch(ch -> ch == '0'))
			return new NumericKeyValidation(NumericKeyValidationReason.NON_NUMERIC_OR_ZERO,
				Optional.empty(), "Account Filter must be a non-zero 11 digit number");
		return new NumericKeyValidation(NumericKeyValidationReason.VALID,
			Optional.of(new NumericKey(text)), "");
	}

	public enum VisitEvent
	{
		BACK, FIRST_DISPLAY, REENTRY, UNEXPECTED
	}

	public enum VisitOutcome
	{
		NAVIGATE, DISPLAY, VALIDATION_ERROR, TERMINAL_TEXT
	}

	public record CallerContext(Optional<String> program,
		Optional<String> transaction)
	{
		public CallerContext
		{
			program = Objects.requireNonNull(program, "program");
			transaction = Objects.requireNonNull(transaction, "transaction");
		}
	}

	public record VisitRequest(VisitEvent event, CallerContext caller,
		Optional<String> normalizedFilter)
	{
		public VisitRequest
		{
			Objects.requireNonNull(event, "event");
			Objects.requireNonNull(caller, "caller");
			normalizedFilter = Objects.requireNonNull(normalizedFilter,
				"normalizedFilter");
		}
	}

	public record VisitNavigation(String program, String transaction)
	{
		public VisitNavigation
		{
			Objects.requireNonNull(program, "program");
			Objects.requireNonNull(transaction, "transaction");
		}
	}

	public record VisitResponse(VisitOutcome outcome,
		Optional<VisitNavigation> navigation,
		Optional<CardCrossReferenceInquiry> inquiry,
		Optional<CardCrossReferencePresentation> view,
		String feedback, boolean suspend)
	{
		public VisitResponse
		{
			Objects.requireNonNull(outcome, "outcome");
			navigation = Objects.requireNonNull(navigation, "navigation");
			inquiry = Objects.requireNonNull(inquiry, "inquiry");
			view = Objects.requireNonNull(view, "view");
			Objects.requireNonNull(feedback, "feedback");
			if ((outcome == VisitOutcome.NAVIGATE) != navigation.isPresent())
				throw new IllegalArgumentException("navigation and outcome disagree");
			if (view.isPresent() && inquiry.isEmpty())
				throw new IllegalArgumentException("view requires inquiry");
		}
	}

	public static VisitResponse decideVisit(VisitRequest request,
		CardCrossReferenceWsCardRidAcctIdXLookupPort cardCrossReferencePort,
		AccountWsCardRidAcctIdXLookupPort accountPort,
		CustomerWsCardRidCustIdXLookupPort customerPort,
		CardCrossReferenceNextKeyPort nextKeyPort,
		String errorMessage, Optional<String> infoMessage)
	{
		VisitRequest value = Objects.requireNonNull(request, "request");
		return switch (value.event())
		{
			case BACK -> new VisitResponse(VisitOutcome.NAVIGATE,
				Optional.of(new VisitNavigation(
					value.caller().program().filter(text -> !text.isBlank())
						.orElse("COMEN01C"),
					value.caller().transaction().filter(text -> !text.isBlank())
						.orElse("CM00"))),
				Optional.empty(), Optional.empty(), "", false);
			case FIRST_DISPLAY -> new VisitResponse(VisitOutcome.DISPLAY,
				Optional.empty(), Optional.empty(),
				Optional.empty(),
				validateNumericKey(Optional.empty()).feedback(), true);
			case REENTRY -> reenterVisit(value, cardCrossReferencePort, accountPort,
				customerPort, nextKeyPort, errorMessage, infoMessage);
			case UNEXPECTED -> new VisitResponse(VisitOutcome.TERMINAL_TEXT,
				Optional.empty(), Optional.empty(),
				Optional.empty(), "UNEXPECTED DATA SCENARIO", false);
		};
	}

	private static VisitResponse reenterVisit(VisitRequest request,
		CardCrossReferenceWsCardRidAcctIdXLookupPort cardCrossReferencePort,
		AccountWsCardRidAcctIdXLookupPort accountPort,
		CustomerWsCardRidCustIdXLookupPort customerPort,
		CardCrossReferenceNextKeyPort nextKeyPort,
		String errorMessage, Optional<String> infoMessage)
	{
		NumericKeyValidation validation = validateNumericKey(
			request.normalizedFilter());
		if (validation.reason() != NumericKeyValidationReason.VALID)
			return new VisitResponse(VisitOutcome.VALIDATION_ERROR,
				Optional.empty(), Optional.empty(),
				Optional.empty(), validation.feedback(), true);
		String key = validation.key().orElseThrow().digits();
		CardCrossReferenceInquiry inquiry = inquireCardCrossReference(
			key, cardCrossReferencePort, accountPort, customerPort, nextKeyPort);
		CardCrossReferencePresentation view = presentCardCrossReference(
			true, Optional.of(key), inquiry,
			Objects.requireNonNull(errorMessage, "errorMessage"),
			Objects.requireNonNull(infoMessage, "infoMessage"));
		return new VisitResponse(VisitOutcome.DISPLAY,
			Optional.empty(), Optional.of(inquiry),
			Optional.of(view), "", true);
	}

	public enum CardCrossReferenceInquiryExit
	{
		FILTER_REJECTED, ACCOUNT_MESSAGE, CUSTOMER_MESSAGE, COMPLETE
	}

	public record CardCrossReferenceInquiry(
		CardCrossReferenceWsCardRidAcctIdXLookupResult cardCrossReference,
		Optional<AccountWsCardRidAcctIdXLookupResult> account,
		Optional<CustomerWsCardRidCustIdXLookupResult> customer,
		CardCrossReferenceInquiryExit exit)
	{
		public CardCrossReferenceInquiry
		{
			Objects.requireNonNull(cardCrossReference, "cardCrossReference");
			account = Objects.requireNonNull(account, "account");
			customer = Objects.requireNonNull(customer, "customer");
			Objects.requireNonNull(exit, "exit");
			if ((exit == CardCrossReferenceInquiryExit.FILTER_REJECTED)
				!= (cardCrossReference.outcome() != CardCrossReferenceWsCardRidAcctIdXLookupOutcome.FOUND))
				throw new IllegalArgumentException("cardCrossReference read and filter exit disagree");
			if (cardCrossReference.outcome() == CardCrossReferenceWsCardRidAcctIdXLookupOutcome.FOUND
				&& account.isEmpty())
				throw new IllegalArgumentException("account read requires cardCrossReference result");
			if (customer.isPresent() != (exit == CardCrossReferenceInquiryExit.CUSTOMER_MESSAGE
				|| exit == CardCrossReferenceInquiryExit.COMPLETE))
				throw new IllegalArgumentException("customer read and exit disagree");
		}
	}

	public interface CardCrossReferenceNextKeyPort
	{
		String from(CardCrossReferenceWsCardRidAcctIdXSnapshot cardCrossReference);
	}

	public static CardCrossReferenceInquiry inquireCardCrossReference(
		String sharedKey, CardCrossReferenceWsCardRidAcctIdXLookupPort cardCrossReferencePort,
		AccountWsCardRidAcctIdXLookupPort accountPort,
		CustomerWsCardRidCustIdXLookupPort customerPort,
		CardCrossReferenceNextKeyPort nextKeyPort)
	{
		String key = Objects.requireNonNull(sharedKey, "sharedKey");
		CardCrossReferenceWsCardRidAcctIdXLookupResult cardCrossReference =
			Objects.requireNonNull(cardCrossReferencePort, "cardCrossReferencePort").find(key);
		if (cardCrossReference.outcome() != CardCrossReferenceWsCardRidAcctIdXLookupOutcome.FOUND)
			return new CardCrossReferenceInquiry(cardCrossReference,
				Optional.empty(), Optional.empty(),
				CardCrossReferenceInquiryExit.FILTER_REJECTED);
		AccountWsCardRidAcctIdXLookupResult account =
			Objects.requireNonNull(accountPort, "accountPort").find(key);
		if (account.message().equals("Did not find this account in account master file"))
			return new CardCrossReferenceInquiry(cardCrossReference,
				Optional.of(account), Optional.empty(),
				CardCrossReferenceInquiryExit.ACCOUNT_MESSAGE);
		String nextKey = Objects.requireNonNull(
			Objects.requireNonNull(nextKeyPort, "nextKeyPort")
				.from(cardCrossReference.value().orElseThrow()), "nextKey");
		CustomerWsCardRidCustIdXLookupResult customer =
			Objects.requireNonNull(customerPort, "customerPort").find(nextKey);
		return new CardCrossReferenceInquiry(cardCrossReference,
			Optional.of(account), Optional.of(customer),
			customer.message().equals("Did not find associated customer in master file")
				? CardCrossReferenceInquiryExit.CUSTOMER_MESSAGE
				: CardCrossReferenceInquiryExit.COMPLETE);
	}

	public record AccountDisplay(
		String acctActiveStatus,
		BigDecimal acctCurrBal,
		BigDecimal acctCreditLimit,
		BigDecimal acctCashCreditLimit,
		BigDecimal acctCurrCycCredit,
		BigDecimal acctCurrCycDebit,
		LocalDate acctOpenDate,
		LocalDate acctExpiraionDate,
		LocalDate acctReissueDate,
		String acctGroupId
	) { }

	public static AccountDisplay toAccountDisplay(
		AccountWsCardRidAcctIdXSnapshot record)
	{
		AccountWsCardRidAcctIdXSnapshot value = Objects.requireNonNull(
			record, "record");
		return new AccountDisplay(
			value.acctActiveStatus(),
			value.acctCurrBal(),
			value.acctCreditLimit(),
			value.acctCashCreditLimit(),
			value.acctCurrCycCredit(),
			value.acctCurrCycDebit(),
			value.acctOpenDate(),
			value.acctExpiraionDate(),
			value.acctReissueDate(),
			value.acctGroupId()
		);
	}

	public record CustomerDisplay(
		long custId,
		long custFicoCreditScore,
		LocalDate custDobYyyyMmDd,
		String custFirstName,
		String custMiddleName,
		String custLastName,
		String custAddrLine1,
		String custAddrLine2,
		String custAddrLine3,
		String custAddrStateCd,
		String custAddrZip,
		String custAddrCountryCd,
		String custPhoneNum1,
		String custPhoneNum2,
		String custGovtIssuedId,
		String custEftAccountId,
		String custPriCardHolderInd,
		String formattedCustSsn
	) { }

	public static CustomerDisplay toCustomerDisplay(
		CustomerWsCardRidCustIdXSnapshot record)
	{
		CustomerWsCardRidCustIdXSnapshot value = Objects.requireNonNull(
			record, "record");
		if (value.custSsn() < 0)
			throw new IllegalArgumentException("negative source value");
		String digits = String.format(Locale.ROOT,
			"%0" + 9 + "d",
			value.custSsn());
		if (digits.length() != 9)
			throw new IllegalArgumentException("source width mismatch");
		int firstEnd = 3;
		int secondEnd = firstEnd + 2;
		int thirdEnd = secondEnd + 4;
		if (thirdEnd != digits.length())
			throw new IllegalStateException("slice width mismatch");
		String formatted = digits.substring(0, firstEnd)
			+ "-"
			+ digits.substring(firstEnd, secondEnd)
			+ "-"
			+ digits.substring(secondEnd, thirdEnd);
		return new CustomerDisplay(
			value.custId(),
			value.custFicoCreditScore(),
			value.custDobYyyyMmDd(),
			value.custFirstName(),
			value.custMiddleName(),
			value.custLastName(),
			value.custAddrLine1(),
			value.custAddrLine2(),
			value.custAddrLine3(),
			value.custAddrStateCd(),
			value.custAddrZip(),
			value.custAddrCountryCd(),
			value.custPhoneNum1(),
			value.custPhoneNum2(),
			value.custGovtIssuedId(),
			value.custEftAccountId(),
			value.custPriCardHolderInd(),
			formatted
		);
	}

	public record CardCrossReferencePresentation(
		Optional<String> filter,
		Optional<AccountDisplay> account,
		Optional<CustomerDisplay> customer,
		String errorMessage, String infoMessage)
	{
		public CardCrossReferencePresentation
		{
			filter = Objects.requireNonNull(filter, "filter");
			account = Objects.requireNonNull(account, "account");
			customer = Objects.requireNonNull(customer, "customer");
			Objects.requireNonNull(errorMessage, "errorMessage");
			Objects.requireNonNull(infoMessage, "infoMessage");
		}
	}

	public static CardCrossReferencePresentation presentCardCrossReference(
		boolean hasInput, Optional<String> requestedFilter,
		CardCrossReferenceInquiry inquiry, String errorMessage,
		Optional<String> infoMessage)
	{
		CardCrossReferenceInquiry value = Objects.requireNonNull(
			inquiry, "inquiry");
		Optional<String> filter = hasInput
			? Objects.requireNonNull(requestedFilter, "requestedFilter")
				.filter(text -> !text.isBlank())
			: Optional.empty();
		Optional<AccountWsCardRidAcctIdXSnapshot> accountValue =
			value.account().flatMap(AccountWsCardRidAcctIdXLookupResult::value);
		Optional<CustomerWsCardRidCustIdXSnapshot> customerValue =
			value.customer().flatMap(CustomerWsCardRidCustIdXLookupResult::value);
		boolean accountBranch = hasInput &&
			(accountValue.isPresent() || customerValue.isPresent());
		Optional<AccountDisplay> account = accountBranch
			? accountValue.map(AccountViewController::toAccountDisplay)
			: Optional.empty();
		Optional<CustomerDisplay> customer = hasInput
			? customerValue.map(AccountViewController::toCustomerDisplay)
			: Optional.empty();
		Optional<String> info = Objects.requireNonNull(
			infoMessage, "infoMessage").filter(text -> !text.isBlank());
		return new CardCrossReferencePresentation(filter, account, customer,
			Objects.requireNonNull(errorMessage, "errorMessage"),
			!hasInput || info.isEmpty()
				? "Enter or update id of account to display" : info.orElseThrow());
	}

	public record CardCrossReferenceWsCardRidAcctIdXSnapshot(
		String xrefCardNum,
		long xrefCustId,
		long xrefAcctId
	) { }

	public static CardCrossReferenceWsCardRidAcctIdXSnapshot toCardCrossReferenceWsCardRidAcctIdXSnapshot(
		CardCrossReference record)
	{
		CardCrossReference value = Objects.requireNonNull(record, "record");
		return new CardCrossReferenceWsCardRidAcctIdXSnapshot(
			value.getXrefCardNum(),
			value.getXrefCustId(),
			value.getXrefAcctId()
		);
	}

	public enum CardCrossReferenceWsCardRidAcctIdXLookupOutcome { FOUND, NOT_FOUND, FAILED }

	public record CardCrossReferenceWsCardRidAcctIdXLookupResult(
		CardCrossReferenceWsCardRidAcctIdXLookupOutcome outcome,
		Optional<CardCrossReferenceWsCardRidAcctIdXSnapshot> value, String message)
	{
		public CardCrossReferenceWsCardRidAcctIdXLookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			Objects.requireNonNull(message, "message");
			if ((outcome == CardCrossReferenceWsCardRidAcctIdXLookupOutcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	public interface CardCrossReferenceWsCardRidAcctIdXLookupPort
	{
		CardCrossReferenceWsCardRidAcctIdXLookupResult find(String key);
	}

	public record AccountWsCardRidAcctIdXSnapshot(
		long acctId,
		String acctActiveStatus,
		BigDecimal acctCurrBal,
		BigDecimal acctCreditLimit,
		BigDecimal acctCashCreditLimit,
		LocalDate acctOpenDate,
		LocalDate acctExpiraionDate,
		LocalDate acctReissueDate,
		BigDecimal acctCurrCycCredit,
		BigDecimal acctCurrCycDebit,
		String acctAddrZip,
		String acctGroupId
	) { }

	public static AccountWsCardRidAcctIdXSnapshot toAccountWsCardRidAcctIdXSnapshot(
		Account record)
	{
		Account value = Objects.requireNonNull(record, "record");
		return new AccountWsCardRidAcctIdXSnapshot(
			value.getAcctId(),
			value.getAcctActiveStatus(),
			value.getAcctCurrBal(),
			value.getAcctCreditLimit(),
			value.getAcctCashCreditLimit(),
			value.getAcctOpenDate(),
			value.getAcctExpiraionDate(),
			value.getAcctReissueDate(),
			value.getAcctCurrCycCredit(),
			value.getAcctCurrCycDebit(),
			value.getAcctAddrZip(),
			value.getAcctGroupId()
		);
	}

	public enum AccountWsCardRidAcctIdXLookupOutcome { FOUND, NOT_FOUND, FAILED }

	public record AccountWsCardRidAcctIdXLookupResult(
		AccountWsCardRidAcctIdXLookupOutcome outcome,
		Optional<AccountWsCardRidAcctIdXSnapshot> value, String message)
	{
		public AccountWsCardRidAcctIdXLookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			Objects.requireNonNull(message, "message");
			if ((outcome == AccountWsCardRidAcctIdXLookupOutcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	public interface AccountWsCardRidAcctIdXLookupPort
	{
		AccountWsCardRidAcctIdXLookupResult find(String key);
	}

	public record CustomerWsCardRidCustIdXSnapshot(
		long custId,
		String custFirstName,
		String custMiddleName,
		String custLastName,
		String custAddrLine1,
		String custAddrLine2,
		String custAddrLine3,
		String custAddrStateCd,
		String custAddrCountryCd,
		String custAddrZip,
		String custPhoneNum1,
		String custPhoneNum2,
		long custSsn,
		String custGovtIssuedId,
		LocalDate custDobYyyyMmDd,
		String custEftAccountId,
		String custPriCardHolderInd,
		long custFicoCreditScore
	) { }

	public static CustomerWsCardRidCustIdXSnapshot toCustomerWsCardRidCustIdXSnapshot(
		Customer record)
	{
		Customer value = Objects.requireNonNull(record, "record");
		return new CustomerWsCardRidCustIdXSnapshot(
			value.getCustId(),
			value.getCustFirstName(),
			value.getCustMiddleName(),
			value.getCustLastName(),
			value.getCustAddrLine1(),
			value.getCustAddrLine2(),
			value.getCustAddrLine3(),
			value.getCustAddrStateCd(),
			value.getCustAddrCountryCd(),
			value.getCustAddrZip(),
			value.getCustPhoneNum1(),
			value.getCustPhoneNum2(),
			value.getCustSsn(),
			value.getCustGovtIssuedId(),
			value.getCustDobYyyyMmDd(),
			value.getCustEftAccountId(),
			value.getCustPriCardHolderInd(),
			value.getCustFicoCreditScore()
		);
	}

	public enum CustomerWsCardRidCustIdXLookupOutcome { FOUND, NOT_FOUND, FAILED }

	public record CustomerWsCardRidCustIdXLookupResult(
		CustomerWsCardRidCustIdXLookupOutcome outcome,
		Optional<CustomerWsCardRidCustIdXSnapshot> value, String message)
	{
		public CustomerWsCardRidCustIdXLookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			value = Objects.requireNonNull(value, "value");
			Objects.requireNonNull(message, "message");
			if ((outcome == CustomerWsCardRidCustIdXLookupOutcome.FOUND) != value.isPresent())
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	public interface CustomerWsCardRidCustIdXLookupPort
	{
		CustomerWsCardRidCustIdXLookupResult find(String key);
	}
}

@Component
final class AccountViewControllerCrossReferenceRepository
	implements AccountViewController.CardCrossReferenceWsCardRidAcctIdXLookupPort,
		AccountViewController.CardCrossReferenceNextKeyPort
{
	private static final Logger log = LoggerFactory.getLogger(AccountViewControllerCrossReferenceRepository.class);
	private final javax.sql.DataSource dataSource;

	public AccountViewControllerCrossReferenceRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource);
	}

	@Override
	public AccountViewController.CardCrossReferenceWsCardRidAcctIdXLookupResult find(String key)
	{
		String sql = "SELECT XREF_CARD_NUM, XREF_CUST_ID, "
			+ "XREF_ACCT_ID FROM CCXREF WHERE "
			+ "XREF_ACCT_ID = ? ORDER BY XREF_CARD_NUM";
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setLong(1, Long.parseLong(key));
			statement.setMaxRows(1);
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next()) return result(
					AccountViewController.CardCrossReferenceWsCardRidAcctIdXLookupOutcome.NOT_FOUND,
					Optional.empty(), "Account cross-reference was not found");
				return result(AccountViewController.CardCrossReferenceWsCardRidAcctIdXLookupOutcome.FOUND,
					Optional.of(new AccountViewController.CardCrossReferenceWsCardRidAcctIdXSnapshot(
						required(row, "XREF_CARD_NUM").trim(),
						row.getLong("XREF_CUST_ID"),
						row.getLong("XREF_ACCT_ID"))), "");
			}
		}
		catch (SQLException | NumberFormatException | NullPointerException failure)
		{
			log.warn("Account cross-reference lookup failed for key {}", key, failure);
			return result(AccountViewController.CardCrossReferenceWsCardRidAcctIdXLookupOutcome.FAILED,
				Optional.empty(), "Account cross-reference lookup failed");
		}
	}

	@Override
	public String from(AccountViewController.CardCrossReferenceWsCardRidAcctIdXSnapshot cardCrossReference)
	{
		return Long.toString(Objects.requireNonNull(cardCrossReference).xrefCustId());
	}

	private static AccountViewController.CardCrossReferenceWsCardRidAcctIdXLookupResult result(
		AccountViewController.CardCrossReferenceWsCardRidAcctIdXLookupOutcome outcome,
		Optional<AccountViewController.CardCrossReferenceWsCardRidAcctIdXSnapshot> value,
		String message)
	{
		return new AccountViewController.CardCrossReferenceWsCardRidAcctIdXLookupResult(
			outcome, value, message);
	}

	private static String required(ResultSet row, String column)
		throws SQLException
	{
		String value = row.getString(column);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}
}

@Component
final class AccountViewControllerAccountRepository
	implements AccountViewController.AccountWsCardRidAcctIdXLookupPort
{
	private static final Logger log = LoggerFactory.getLogger(AccountViewControllerAccountRepository.class);
	private final javax.sql.DataSource dataSource;

	public AccountViewControllerAccountRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource);
	}

	@Override
	public AccountViewController.AccountWsCardRidAcctIdXLookupResult find(String key)
	{
		String sql = "SELECT ACCT_ID, ACCT_ACTIVE_STATUS, "
			+ "ACCT_CURR_BAL, ACCT_CREDIT_LIMIT, "
			+ "ACCT_CASH_CREDIT_LIMIT, ACCT_OPEN_DATE, "
			+ "ACCT_EXPIRAION_DATE, ACCT_REISSUE_DATE, "
			+ "ACCT_CURR_CYC_CREDIT, ACCT_CURR_CYC_DEBIT, "
			+ "ACCT_ADDR_ZIP, ACCT_GROUP_ID FROM "
			+ "ACCTDAT WHERE ACCT_ID = ?";
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setLong(1, Long.parseLong(key));
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next()) return result(
					AccountViewController.AccountWsCardRidAcctIdXLookupOutcome.NOT_FOUND,
					Optional.empty(),
					"Did not find this account in account master file");
				AccountViewController.AccountWsCardRidAcctIdXSnapshot value =
					new AccountViewController.AccountWsCardRidAcctIdXSnapshot(
						row.getLong("ACCT_ID"),
						required(row, "ACCT_ACTIVE_STATUS").trim(),
						decimal(row, "ACCT_CURR_BAL"),
						decimal(row, "ACCT_CREDIT_LIMIT"),
						decimal(row, "ACCT_CASH_CREDIT_LIMIT"),
						date(row, "ACCT_OPEN_DATE"),
						date(row, "ACCT_EXPIRAION_DATE"),
						date(row, "ACCT_REISSUE_DATE"),
						decimal(row, "ACCT_CURR_CYC_CREDIT"),
						decimal(row, "ACCT_CURR_CYC_DEBIT"),
						required(row, "ACCT_ADDR_ZIP").trim(),
						required(row, "ACCT_GROUP_ID").trim());
				return result(AccountViewController.AccountWsCardRidAcctIdXLookupOutcome.FOUND,
					Optional.of(value), "");
			}
		}
		catch (SQLException | NumberFormatException | NullPointerException failure)
		{
			log.warn("Account lookup failed for key {}", key, failure);
			return result(AccountViewController.AccountWsCardRidAcctIdXLookupOutcome.FAILED,
				Optional.empty(), "Account lookup failed");
		}
	}

	private static AccountViewController.AccountWsCardRidAcctIdXLookupResult result(
		AccountViewController.AccountWsCardRidAcctIdXLookupOutcome outcome,
		Optional<AccountViewController.AccountWsCardRidAcctIdXSnapshot> value,
		String message)
	{
		return new AccountViewController.AccountWsCardRidAcctIdXLookupResult(
			outcome, value, message);
	}

	private static String required(ResultSet row, String column)
		throws SQLException
	{
		String value = row.getString(column);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}

	private static BigDecimal decimal(ResultSet row,
		String column) throws SQLException
	{
		BigDecimal value = row.getBigDecimal(column);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}

	// The project types these source dates as LocalDate (#field_java_types).
	// The column holds the ISO text, and a blank one reads as no date.
	private static LocalDate date(ResultSet row, String column)
		throws SQLException
	{
		String value = required(row, column).trim();
		return value.isEmpty() ? null : LocalDate.parse(value);
	}
}

@Component
final class AccountViewControllerCustomerRepository
	implements AccountViewController.CustomerWsCardRidCustIdXLookupPort
{
	private static final Logger log = LoggerFactory.getLogger(AccountViewControllerCustomerRepository.class);
	private final javax.sql.DataSource dataSource;

	public AccountViewControllerCustomerRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource);
	}

	@Override
	public AccountViewController.CustomerWsCardRidCustIdXLookupResult find(String key)
	{
		String sql = "SELECT CUST_ID, CUST_FIRST_NAME, "
			+ "CUST_MIDDLE_NAME, CUST_LAST_NAME, "
			+ "CUST_ADDR_LINE_1, CUST_ADDR_LINE_2, "
			+ "CUST_ADDR_LINE_3, CUST_ADDR_STATE_CD, "
			+ "CUST_ADDR_COUNTRY_CD, CUST_ADDR_ZIP, "
			+ "CUST_PHONE_NUM_1, CUST_PHONE_NUM_2, "
			+ "CUST_SSN, CUST_GOVT_ISSUED_ID, "
			+ "CUST_DOB_YYYY_MM_DD, CUST_EFT_ACCOUNT_ID, "
			+ "CUST_PRI_CARD_HOLDER_IND, CUST_FICO_CREDIT_SCORE FROM "
			+ "CUSTDAT WHERE CUST_ID = ?";
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setLong(1, Long.parseLong(key));
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next()) return result(
					AccountViewController.CustomerWsCardRidCustIdXLookupOutcome.NOT_FOUND,
					Optional.empty(),
					"Did not find associated customer in master file");
				AccountViewController.CustomerWsCardRidCustIdXSnapshot value =
					new AccountViewController.CustomerWsCardRidCustIdXSnapshot(
						row.getLong("CUST_ID"),
						required(row, "CUST_FIRST_NAME").stripTrailing(),
						required(row, "CUST_MIDDLE_NAME").stripTrailing(),
						required(row, "CUST_LAST_NAME").stripTrailing(),
						required(row, "CUST_ADDR_LINE_1").stripTrailing(),
						required(row, "CUST_ADDR_LINE_2").stripTrailing(),
						required(row, "CUST_ADDR_LINE_3").stripTrailing(),
						required(row, "CUST_ADDR_STATE_CD").trim(),
						required(row, "CUST_ADDR_COUNTRY_CD").trim(),
						required(row, "CUST_ADDR_ZIP").trim(),
						required(row, "CUST_PHONE_NUM_1").trim(),
						required(row, "CUST_PHONE_NUM_2").trim(),
						row.getLong("CUST_SSN"),
						required(row, "CUST_GOVT_ISSUED_ID").trim(),
						date(row, "CUST_DOB_YYYY_MM_DD"),
						required(row, "CUST_EFT_ACCOUNT_ID").trim(),
						required(row, "CUST_PRI_CARD_HOLDER_IND").trim(),
						row.getLong("CUST_FICO_CREDIT_SCORE"));
				return result(AccountViewController.CustomerWsCardRidCustIdXLookupOutcome.FOUND,
					Optional.of(value), "");
			}
		}
		catch (SQLException | NumberFormatException | NullPointerException failure)
		{
			log.warn("Customer lookup failed for key {}", key, failure);
			return result(AccountViewController.CustomerWsCardRidCustIdXLookupOutcome.FAILED,
				Optional.empty(), "Customer lookup failed");
		}
	}

	private static AccountViewController.CustomerWsCardRidCustIdXLookupResult result(
		AccountViewController.CustomerWsCardRidCustIdXLookupOutcome outcome,
		Optional<AccountViewController.CustomerWsCardRidCustIdXSnapshot> value,
		String message)
	{
		return new AccountViewController.CustomerWsCardRidCustIdXLookupResult(
			outcome, value, message);
	}

	private static String required(ResultSet row, String column)
		throws SQLException
	{
		String value = row.getString(column);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}

	// The project types these source dates as LocalDate (#field_java_types).
	// The column holds the ISO text, and a blank one reads as no date.
	private static LocalDate date(ResultSet row, String column)
		throws SQLException
	{
		String value = required(row, column).trim();
		return value.isEmpty() ? null : LocalDate.parse(value);
	}
}
