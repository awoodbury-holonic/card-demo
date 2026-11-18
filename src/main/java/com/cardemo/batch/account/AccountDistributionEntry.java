package com.cardemo.batch.account;

import java.math.BigDecimal;

public final class AccountDistributionEntry
{
	private long outAcctId;

	public long getOutAcctId()
	{
		return outAcctId;
	}

	public void setOutAcctId(long outAcctId)
	{
		this.outAcctId = outAcctId;
	}
	private String outAcctActiveStatus;

	public String getOutAcctActiveStatus()
	{
		return outAcctActiveStatus;
	}

	public void setOutAcctActiveStatus(String outAcctActiveStatus)
	{
		this.outAcctActiveStatus = outAcctActiveStatus;
	}
	private BigDecimal outAcctCurrBal;

	public BigDecimal getOutAcctCurrBal()
	{
		return outAcctCurrBal;
	}

	public void setOutAcctCurrBal(BigDecimal outAcctCurrBal)
	{
		this.outAcctCurrBal = outAcctCurrBal;
	}
	private BigDecimal outAcctCreditLimit;

	public BigDecimal getOutAcctCreditLimit()
	{
		return outAcctCreditLimit;
	}

	public void setOutAcctCreditLimit(BigDecimal outAcctCreditLimit)
	{
		this.outAcctCreditLimit = outAcctCreditLimit;
	}
	private BigDecimal outAcctCashCreditLimit;

	public BigDecimal getOutAcctCashCreditLimit()
	{
		return outAcctCashCreditLimit;
	}

	public void setOutAcctCashCreditLimit(BigDecimal outAcctCashCreditLimit)
	{
		this.outAcctCashCreditLimit = outAcctCashCreditLimit;
	}
	private String outAcctOpenDate;

	public String getOutAcctOpenDate()
	{
		return outAcctOpenDate;
	}

	public void setOutAcctOpenDate(String outAcctOpenDate)
	{
		this.outAcctOpenDate = outAcctOpenDate;
	}
	private String outAcctExpiraionDate;

	public String getOutAcctExpiraionDate()
	{
		return outAcctExpiraionDate;
	}

	public void setOutAcctExpiraionDate(String outAcctExpiraionDate)
	{
		this.outAcctExpiraionDate = outAcctExpiraionDate;
	}
	private String outAcctReissueDate;

	public String getOutAcctReissueDate()
	{
		return outAcctReissueDate;
	}

	public void setOutAcctReissueDate(String outAcctReissueDate)
	{
		this.outAcctReissueDate = outAcctReissueDate;
	}
	private BigDecimal outAcctCurrCycCredit;

	public BigDecimal getOutAcctCurrCycCredit()
	{
		return outAcctCurrCycCredit;
	}

	public void setOutAcctCurrCycCredit(BigDecimal outAcctCurrCycCredit)
	{
		this.outAcctCurrCycCredit = outAcctCurrCycCredit;
	}
	private BigDecimal outAcctCurrCycDebit;

	public BigDecimal getOutAcctCurrCycDebit()
	{
		return outAcctCurrCycDebit;
	}

	public void setOutAcctCurrCycDebit(BigDecimal outAcctCurrCycDebit)
	{
		this.outAcctCurrCycDebit = outAcctCurrCycDebit;
	}
	private String outAcctGroupId;

	public String getOutAcctGroupId()
	{
		return outAcctGroupId;
	}

	public void setOutAcctGroupId(String outAcctGroupId)
	{
		this.outAcctGroupId = outAcctGroupId;
	}
}
