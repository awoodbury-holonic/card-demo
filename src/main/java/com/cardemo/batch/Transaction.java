package com.cardemo.batch;

import java.math.BigDecimal;

public final class Transaction
{
	private String tranId;

	public String getTranId()
	{
		return tranId;
	}

	public void setTranId(String tranId)
	{
		this.tranId = tranId;
	}
	private String tranTypeCd;

	public String getTranTypeCd()
	{
		return tranTypeCd;
	}

	public void setTranTypeCd(String tranTypeCd)
	{
		this.tranTypeCd = tranTypeCd;
	}
	private long tranCatCd;

	public long getTranCatCd()
	{
		return tranCatCd;
	}

	public void setTranCatCd(long tranCatCd)
	{
		this.tranCatCd = tranCatCd;
	}
	private String tranSource;

	public String getTranSource()
	{
		return tranSource;
	}

	public void setTranSource(String tranSource)
	{
		this.tranSource = tranSource;
	}
	private String tranDesc;

	public String getTranDesc()
	{
		return tranDesc;
	}

	public void setTranDesc(String tranDesc)
	{
		this.tranDesc = tranDesc;
	}
	private BigDecimal tranAmt;

	public BigDecimal getTranAmt()
	{
		return tranAmt;
	}

	public void setTranAmt(BigDecimal tranAmt)
	{
		this.tranAmt = tranAmt;
	}
	private long tranMerchantId;

	public long getTranMerchantId()
	{
		return tranMerchantId;
	}

	public void setTranMerchantId(long tranMerchantId)
	{
		this.tranMerchantId = tranMerchantId;
	}
	private String tranMerchantName;

	public String getTranMerchantName()
	{
		return tranMerchantName;
	}

	public void setTranMerchantName(String tranMerchantName)
	{
		this.tranMerchantName = tranMerchantName;
	}
	private String tranMerchantCity;

	public String getTranMerchantCity()
	{
		return tranMerchantCity;
	}

	public void setTranMerchantCity(String tranMerchantCity)
	{
		this.tranMerchantCity = tranMerchantCity;
	}
	private String tranMerchantZip;

	public String getTranMerchantZip()
	{
		return tranMerchantZip;
	}

	public void setTranMerchantZip(String tranMerchantZip)
	{
		this.tranMerchantZip = tranMerchantZip;
	}
	private String tranCardNum;

	public String getTranCardNum()
	{
		return tranCardNum;
	}

	public void setTranCardNum(String tranCardNum)
	{
		this.tranCardNum = tranCardNum;
	}
	private String tranOrigTs;

	public String getTranOrigTs()
	{
		return tranOrigTs;
	}

	public void setTranOrigTs(String tranOrigTs)
	{
		this.tranOrigTs = tranOrigTs;
	}
	private String tranProcTs;

	public String getTranProcTs()
	{
		return tranProcTs;
	}

	public void setTranProcTs(String tranProcTs)
	{
		this.tranProcTs = tranProcTs;
	}
}
