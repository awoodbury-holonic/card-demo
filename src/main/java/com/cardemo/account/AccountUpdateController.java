package com.cardemo.account;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public final class AccountUpdateController
{


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

	public void handle()
	{
		throw new UnsupportedOperationException(
			"generated controller requires further Style refinement");
	}
}
