import { useEffect, useState } from "react";
import PageNavigation, { usePagedRecords } from "../components/PagedRecords.jsx";

const dataset = { endpoint: "/api/authorizations/pending", rowsProperty: "rows", nextKeyProperty: "nextKey",
	cursorParameter: "startKey", errorProperty: "error", messageProperty: "message",
	failureMessage: "Unable to read authorizations." };

export default function COPAU0A()
{
	const initial = new URLSearchParams(window.location.search).get("accountId") || "";
	const [accountId, setAccountId] = useState(initial);
	const listing = usePagedRecords(dataset, initial ? { accountId: initial, startKey: "" } : null);
	const { data: summary, rows, loading: busy, error } = listing;
	const [selections, setSelections] = useState(["", "", "", "", ""]);
	const [message, setMessage] = useState("");
	const shown = Boolean(summary);

	useEffect(() =>
	{
		setSelections(["", "", "", "", ""]);
		setMessage("");
	}, [summary]);

	function enter(event)
	{
		event.preventDefault();
		const at = selections.findIndex(value => value.trim() !== "");
		if (at >= 0 && at < rows.length && summary && summary.accountId === accountId.trim().padStart(11, "0"))
		{
			const choice = selections[at].trim();
			if (choice === "S" || choice === "s")
			{
				window.location.assign("/authorizations/pending?operation=view&accountId=" + encodeURIComponent(summary.accountId)
					+ "&key=" + encodeURIComponent(rows[at].key));
				return;
			}
			setMessage("Invalid selection. Valid value is S");
			return;
		}
		setMessage("");
		listing.search({ accountId: accountId.trim(), startKey: "" });
	}

	return (
		<section data-screen="COPAU0A">
			<h1>View Authorizations</h1>
			<form id="COPAU0A-search" onSubmit={enter}>
				<label>Search Acct Id:
					<input name="ACCTID" value={accountId} maxLength="11"
						onChange={(event) => setAccountId(event.target.value)} />
				</label>
				<button type="submit" disabled={busy}>Enter</button>
			</form>
			{shown && <section className="detail-table-card">
				<div className="detail-table-scroll">
					<table className="detail-table" aria-label="View Authorizations">
						<tbody>
							<tr><th scope="row">Name:</th><td data-field="CNAME">{summary.name}</td></tr>
							<tr><th scope="row">Customer Id:</th><td data-field="CUSTID">{summary.customerId}</td></tr>
							<tr><th scope="row">Address:</th><td><span data-field="ADDR001">{summary.address1}</span><br /><span data-field="ADDR002">{summary.address2}</span></td></tr>
							<tr><th scope="row">PH:</th><td data-field="PHONE1">{summary.phone}</td></tr>
							<tr><th scope="row">Approval #:</th><td data-field="APPRCNT">{summary.approvedCount}</td></tr>
							<tr><th scope="row">Decline #:</th><td data-field="DECLCNT">{summary.declinedCount}</td></tr>
							<tr><th scope="row">Credit Lim:</th><td data-field="CREDLIM">{summary.creditLimit}</td></tr>
							<tr><th scope="row">Cash Lim:</th><td data-field="CASHLIM">{summary.cashLimit}</td></tr>
							<tr><th scope="row">Appr Amt:</th><td data-field="APPRAMT">{summary.approvedAmount}</td></tr>
							<tr><th scope="row">Credit Bal:</th><td data-field="CREDBAL">{summary.creditBalance}</td></tr>
							<tr><th scope="row">Cash Bal:</th><td data-field="CASHBAL">{summary.cashBalance}</td></tr>
							<tr><th scope="row">Decl Amt:</th><td data-field="DECLAMT">{summary.declinedAmount}</td></tr>
						</tbody>
					</table>
				</div>
			</section>}
			{shown && rows.length > 0 && <div className="data-table-card">
				<div className="data-table-scroll">
					<table className="data-table">
						<thead>
							<tr>
								<th>Sel</th><th>Transaction ID</th><th>Date</th>
								<th>Time</th><th>Type</th><th>A/D</th>
								<th>STS</th><th>Amount</th>
							</tr>
						</thead>
						<tbody>
							{rows.map((row, index) => (
								<tr key={row.key} data-key={row.key}>
									<td><input form="COPAU0A-search" aria-label={"Sel " + (index + 1)} maxLength="1"
										value={selections[index]}
										onChange={(event) => setSelections(selections.map((value, i) =>
											i === index ? event.target.value : value))} /></td>
									<td>{row.transactionId}</td><td>{row.date}</td><td>{row.time}</td><td>{row.type}</td>
									<td>{row.approval}</td><td>{row.matchStatus}</td><td>{row.amount}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			</div>}
			<p role="status">{error || message || (shown && rows.length === 0 ? "No pending authorizations for this account." : "")}</p>
			<PageNavigation page={ listing }>
				<a href="/menu">Back to menu</a>
			</PageNavigation>
		</section>
	);
}
