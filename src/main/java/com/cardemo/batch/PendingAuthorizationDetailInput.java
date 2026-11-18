package com.cardemo.batch;

public final class PendingAuthorizationDetailInput
{
	private long rootSegKey;

	public long getRootSegKey()
	{
		return rootSegKey;
	}

	public void setRootSegKey(long rootSegKey)
	{
		this.rootSegKey = rootSegKey;
	}
	private String childSegRec;

	public String getChildSegRec()
	{
		return childSegRec;
	}

	public void setChildSegRec(String childSegRec)
	{
		this.childSegRec = childSegRec;
	}
}
