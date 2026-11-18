package com.cardemo.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// The source submits a job, not data: its job stream (one 80-byte line each,
// up to and including /*EOF), with the report's start and end dates in the
// sort step's symbols and the report step's date parameters, goes to the
// internal reader's queue. A monthly report covers the current month, a
// yearly one the current year.
@org.springframework.stereotype.Repository
public class TransactionReportRequestReportJobRecordJdbcAdapter
	implements TransactionReportRequestController.JobRecordPort
{
	private static final String JOB = "//TRNRPT00 JOB 'TRAN REPORT',CLASS=A,MSGCLASS=0,|// NOTIFY=&SYSUID|//*|//JOBLIB JCLLIB ORDER=('AWS.M2.CARDDEMO.PROC')|//*|//STEP10 EXEC PROC=TRANREPT|//*|//STEP05R.SYMNAMES DD *|TRAN-CARD-NUM,263,16,ZD|TRAN-PROC-DT,305,10,CH|PARM-START-DATE,C'@START@'|PARM-END-DATE,C'@END@'|/*|//STEP10R.DATEPARM DD *|@START@ @END@|/*|/*EOF";

	@Override
	public List<String> records(TransactionReportRequestController.ReportType reportType,
		Optional<LocalDate> startDate,
		Optional<LocalDate> endDate)
	{
		LocalDate today = LocalDate.now();
		LocalDate start = switch (reportType)
		{
			case MONTHLY -> today.withDayOfMonth(1);
			case YEARLY -> today.withDayOfYear(1);
			case CUSTOM -> startDate.orElseThrow();
		};
		LocalDate end = switch (reportType)
		{
			case MONTHLY -> today.withDayOfMonth(today.lengthOfMonth());
			case YEARLY -> today.withMonth(12).withDayOfMonth(31);
			case CUSTOM -> endDate.orElseThrow();
		};
		ArrayList<String> records = new ArrayList<>();
		for (String line : JOB.split("\\|", -1))
		{
			String image = line.replace("@START@", start.toString()).replace("@END@", end.toString());
			records.add(image.length() >= 80 ? image.substring(0, 80) : image + " ".repeat(80 - image.length()));
		}
		return List.copyOf(records);
	}
}
