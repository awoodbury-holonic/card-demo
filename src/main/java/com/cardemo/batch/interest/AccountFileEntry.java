package com.cardemo.batch.interest;

public final class AccountFileEntry
{
	private long fdAcctId;

	public long getFdAcctId()
	{
		return fdAcctId;
	}

	public void setFdAcctId(long fdAcctId)
	{
		this.fdAcctId = fdAcctId;
	}
	private String fdAcctData;

	public String getFdAcctData()
	{
		return fdAcctData;
	}

	public void setFdAcctData(String fdAcctData)
	{
		this.fdAcctData = fdAcctData;
	}
}
