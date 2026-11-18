import { useEffect, useState } from "react";

export default function CORPT0A()
{
	const [reportType, setReportType] = useState("MONTHLY");
	const [startDate, setStartDate] = useState("");
	const [endDate, setEndDate] = useState("");
	const [confirmation, setConfirmation] = useState("");
	const [message, setMessage] = useState("");
	const [busy, setBusy] = useState(false);
	const [reports, setReports] = useState([]);
	const [historyMessage, setHistoryMessage] = useState("");
	const [refresh, setRefresh] = useState(0);

	useEffect(() =>
	{
		const controller = new AbortController();
		let timer;
		async function loadReports()
		{
			try
			{
				const response = await fetch("/api/reports/transactions", {
					credentials: "same-origin", signal: controller.signal
				});
				if (!response.ok) throw new Error("report history unavailable");
				const rows = await response.json();
				if (!Array.isArray(rows)) throw new Error("invalid report history");
				if (controller.signal.aborted) return;
				setReports(rows);
				setHistoryMessage("");
				if (rows.some(row => ["QUEUED", "STARTING", "STARTED", "STOPPING", "UNKNOWN"].includes(row.status)))
					timer = setTimeout(loadReports, 1000);
			}
			catch (failure)
			{
				if (!controller.signal.aborted) setHistoryMessage("Unable to load report status. Please refresh.");
			}
		}
		loadReports();
		return () => { controller.abort(); clearTimeout(timer); };
	}, [refresh]);

	async function submit(event)
	{
		event.preventDefault();
		setBusy(true);
		setMessage("");
		try
		{
			const response = await fetch("/api/reports/transactions", {
				method: "POST", credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({reportType, startDate, endDate, confirmation})
			});
			const result = await response.json();
			if (response.ok && ["SUBMITTED", "CANCELLED"].includes(result.outcome)) clear();
			if (response.ok && result.outcome === "SUBMITTED") setRefresh(value => value + 1);
			setMessage(result.message || "Unable to submit report.");
		}
		catch (failure)
		{
			setMessage("Report service is unavailable.");
		}
		finally
		{
			setBusy(false);
		}
	}

	function clear()
	{
		setReportType("MONTHLY");
		setStartDate("");
		setEndDate("");
		setConfirmation("");
		setMessage("");
	}

	return (
		<section data-screen="CORPT0A">
			<h1>Transaction report</h1>
			<form onSubmit={submit}>
				<label>Report period
					<select value={reportType}
						onChange={event => setReportType(event.target.value)}>
						<option value="MONTHLY">Monthly</option>
						<option value="YEARLY">Yearly</option>
						<option value="CUSTOM">Custom range</option>
					</select>
				</label>
				{reportType === "CUSTOM" && <>
					<label>Start date
						<input type="date" value={startDate}
							onChange={event => setStartDate(event.target.value)} required />
					</label>
					<label>End date
						<input type="date" value={endDate}
							onChange={event => setEndDate(event.target.value)} required />
					</label>
				</>}
				<label>Confirm (Y/N)
					<input value={confirmation} maxLength="1"
						onChange={event => setConfirmation(event.target.value)} />
				</label>
				<button type="submit" disabled={busy}>
					{busy ? "Submitting report..." : "Submit report"}
				</button>
				<button type="button" onClick={clear}>Clear</button>
			</form>
			{message && <p role="status">{message}</p>}
			<section aria-label="Recent reports">
				<h2>Recent reports</h2>
				<button type="button" onClick={ () => setRefresh(value => value + 1) }>Refresh reports</button>
				{ historyMessage && <p role="alert">{ historyMessage }</p> }
				{ reports.length === 0 && !historyMessage && <p>No reports submitted yet.</p> }
				{ reports.length > 0 && <table aria-label="Recent reports" className="data-table">
					<thead><tr><th scope="col">Submitted</th><th scope="col">Status</th><th scope="col">Report</th></tr></thead>
					<tbody>{ reports.map(report => <tr key={ report.submissionId } data-submission-id={ report.submissionId }>
						<td>{ report.submittedAt ? new Date(report.submittedAt).toLocaleString() : "" }</td>
						<td>{ report.status }{ report.message && <p>{ report.message }</p> }</td>
						<td>{ report.downloadUrl && <>
							<a href={ report.downloadUrl } target="_blank" rel="noopener noreferrer">View</a>{ " " }
							<a href={ report.downloadUrl + "?download=true" }>Download</a>
						</> }</td>
					</tr>) }</tbody>
				</table> }
			</section>
			<a href="/menu">Back to menu</a>
		</section>
	);
}
