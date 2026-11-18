package com.cardemo.batch;

import java.math.BigDecimal;
import java.util.Objects;

public interface TransactionDetailReportReportService
{
 	record ReportDetail(
		String transactionId,
		long accountId,
		String transactionTypeCode,
		String transactionTypeDescription,
		long categoryCode,
		String categoryDescription,
		String source,
		BigDecimal amount
	) { }
	void finishReport();
	void finishGroup();
	record ReportWindow(
		String wsDateparmRecordWsStartDate,
		String wsDateparmRecordWsEndDate
	) { }
	void generate();
	void initializeReport(ReportWindow window);
	void append(ReportDetail detail);
	record EofInput(BigDecimal amount)
	{
		public EofInput
		{
			Objects.requireNonNull(amount, "amount");
		}
	}

	record ReportTotals(BigDecimal page, BigDecimal group,
		BigDecimal grand)
	{
		public ReportTotals
		{
			Objects.requireNonNull(page, "page");
			Objects.requireNonNull(group, "group");
			Objects.requireNonNull(grand, "grand");
		}
	}

	record EofRollup(ReportTotals remaining,
		BigDecimal pageTotal, BigDecimal grandTotal)
	{
		public EofRollup
		{
			Objects.requireNonNull(remaining, "remaining");
			Objects.requireNonNull(pageTotal, "pageTotal");
			Objects.requireNonNull(grandTotal, "grandTotal");
		}
	}

	interface ReportSink
	{
		void pageTotal(BigDecimal amount);
		void grandTotal(BigDecimal amount);
	}

	static EofRollup finishEof(ReportTotals current, EofInput eof,
		ReportSink sink)
	{
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(eof, "eof");
		Objects.requireNonNull(sink, "sink");
		BigDecimal pageTotal = current.page().add(eof.amount());
		BigDecimal groupTotal = current.group().add(eof.amount());
		sink.pageTotal(pageTotal);
		BigDecimal grandTotal = current.grand().add(pageTotal);
		sink.grandTotal(grandTotal);
		return new EofRollup(new ReportTotals(BigDecimal.ZERO,
			groupTotal, grandTotal), pageTotal, grandTotal);
	}

}
