package com.cardemo.exportimport;

import java.time.LocalDate;

public final class Card
{
	private String cardNum;

	public String getCardNum()
	{
		return cardNum;
	}

	public void setCardNum(String cardNum)
	{
		this.cardNum = cardNum;
	}
	private long cardAcctId;

	public long getCardAcctId()
	{
		return cardAcctId;
	}

	public void setCardAcctId(long cardAcctId)
	{
		this.cardAcctId = cardAcctId;
	}
	private long cardCvvCd;

	public long getCardCvvCd()
	{
		return cardCvvCd;
	}

	public void setCardCvvCd(long cardCvvCd)
	{
		this.cardCvvCd = cardCvvCd;
	}
	private String cardEmbossedName;

	public String getCardEmbossedName()
	{
		return cardEmbossedName;
	}

	public void setCardEmbossedName(String cardEmbossedName)
	{
		this.cardEmbossedName = cardEmbossedName;
	}
	private LocalDate cardExpiraionDate;

	public LocalDate getCardExpiraionDate()
	{
		return cardExpiraionDate;
	}

	public void setCardExpiraionDate(LocalDate cardExpiraionDate)
	{
		this.cardExpiraionDate = cardExpiraionDate;
	}
	private String cardActiveStatus;

	public String getCardActiveStatus()
	{
		return cardActiveStatus;
	}

	public void setCardActiveStatus(String cardActiveStatus)
	{
		this.cardActiveStatus = cardActiveStatus;
	}
}
