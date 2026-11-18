package com.cardemo.exportimport;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Account
{
	private long acctId;

	public long getAcctId()
	{
		return acctId;
	}

	public void setAcctId(long acctId)
	{
		this.acctId = acctId;
	}
	private String acctActiveStatus;

	public String getAcctActiveStatus()
	{
		return acctActiveStatus;
	}

	public void setAcctActiveStatus(String acctActiveStatus)
	{
		this.acctActiveStatus = acctActiveStatus;
	}
	private BigDecimal acctCurrBal;

	public BigDecimal getAcctCurrBal()
	{
		return acctCurrBal;
	}

	public void setAcctCurrBal(BigDecimal acctCurrBal)
	{
		this.acctCurrBal = acctCurrBal;
	}
	private BigDecimal acctCreditLimit;

	public BigDecimal getAcctCreditLimit()
	{
		return acctCreditLimit;
	}

	public void setAcctCreditLimit(BigDecimal acctCreditLimit)
	{
		this.acctCreditLimit = acctCreditLimit;
	}
	private BigDecimal acctCashCreditLimit;

	public BigDecimal getAcctCashCreditLimit()
	{
		return acctCashCreditLimit;
	}

	public void setAcctCashCreditLimit(BigDecimal acctCashCreditLimit)
	{
		this.acctCashCreditLimit = acctCashCreditLimit;
	}
	private LocalDate acctOpenDate;

	public LocalDate getAcctOpenDate()
	{
		return acctOpenDate;
	}

	public void setAcctOpenDate(LocalDate acctOpenDate)
	{
		this.acctOpenDate = acctOpenDate;
	}
	private LocalDate acctExpiraionDate;

	public LocalDate getAcctExpiraionDate()
	{
		return acctExpiraionDate;
	}

	public void setAcctExpiraionDate(LocalDate acctExpiraionDate)
	{
		this.acctExpiraionDate = acctExpiraionDate;
	}
	private LocalDate acctReissueDate;

	public LocalDate getAcctReissueDate()
	{
		return acctReissueDate;
	}

	public void setAcctReissueDate(LocalDate acctReissueDate)
	{
		this.acctReissueDate = acctReissueDate;
	}
	private BigDecimal acctCurrCycCredit;

	public BigDecimal getAcctCurrCycCredit()
	{
		return acctCurrCycCredit;
	}

	public void setAcctCurrCycCredit(BigDecimal acctCurrCycCredit)
	{
		this.acctCurrCycCredit = acctCurrCycCredit;
	}
	private BigDecimal acctCurrCycDebit;

	public BigDecimal getAcctCurrCycDebit()
	{
		return acctCurrCycDebit;
	}

	public void setAcctCurrCycDebit(BigDecimal acctCurrCycDebit)
	{
		this.acctCurrCycDebit = acctCurrCycDebit;
	}
	private String acctAddrZip;

	public String getAcctAddrZip()
	{
		return acctAddrZip;
	}

	public void setAcctAddrZip(String acctAddrZip)
	{
		this.acctAddrZip = acctAddrZip;
	}
	private String acctGroupId;

	public String getAcctGroupId()
	{
		return acctGroupId;
	}

	public void setAcctGroupId(String acctGroupId)
	{
		this.acctGroupId = acctGroupId;
	}
}
