package com.cardemo.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TransactionDetailReportTasklet
{
	private static final Logger log = LoggerFactory.getLogger(TransactionDetailReportTasklet.class);
	private final TransactionDetailReportReportService reportService;

	public TransactionDetailReportTasklet(TransactionDetailReportReportService reportService)
	{
		this.reportService = reportService;
	}

	public void execute()
	{
		reportService.generate();
	}
}
