package com.cardemo.batch;

public final class CardCrossReference
{
	private String xrefCardNum;

	public String getXrefCardNum()
	{
		return xrefCardNum;
	}

	public void setXrefCardNum(String xrefCardNum)
	{
		this.xrefCardNum = xrefCardNum;
	}
	private long xrefCustId;

	public long getXrefCustId()
	{
		return xrefCustId;
	}

	public void setXrefCustId(long xrefCustId)
	{
		this.xrefCustId = xrefCustId;
	}
	private long xrefAcctId;

	public long getXrefAcctId()
	{
		return xrefAcctId;
	}

	public void setXrefAcctId(long xrefAcctId)
	{
		this.xrefAcctId = xrefAcctId;
	}
}
