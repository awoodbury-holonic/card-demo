import { useState } from "react";
import PageNavigation, { usePagedRecords } from "../components/PagedRecords.jsx";

const dataset = { endpoint: "/api/accounts", rowsProperty: "rows", hasNextProperty: "hasNext",
	nextKeyProperty: "lastKey", cursorParameter: "startKey",
	nextParameters: { direction: "FORWARD", anchorMode: "EXCLUDE_ANCHOR" } };

export default function AccountListPage()
{
	const [draftKey, setDraftKey] = useState("");
	const listing = usePagedRecords(dataset, { startKey: "", direction: "FORWARD", anchorMode: "INCLUDE_ANCHOR" });
	const { rows, loading, error } = listing;
	const currency = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });
	function search(event)
	{
		event.preventDefault();
		listing.search({ startKey: draftKey.trim(), direction: "FORWARD", anchorMode: "INCLUDE_ANCHOR" });
	}

	return (
		<main>
			<h1>Accounts</h1>
			<p>Browse accounts or start at an account number.</p>
			<form onSubmit={ search }>
				<label htmlFor="account-list-key">Starting account ID</label>
				<input id="account-list-key" value={ draftKey } maxLength="11"
					inputMode="numeric" onChange={(event) => setDraftKey(event.target.value)} />
				<button type="submit" disabled={ loading }>Search</button>
			</form>
			{error && <p role="alert">{ error }</p>}
			{loading && <p role="status">Loading accounts…</p>}
			{!loading && !error && rows.length === 0 && <p role="status">No accounts found.</p>}
			<div className="data-table-card" aria-busy={ loading }><div className="data-table-scroll">
				<table className="data-table" aria-label="Accounts">
					<thead><tr><th>Account ID</th><th>Status</th><th>Current balance</th><th>Credit limit</th><th>Actions</th></tr></thead>
					<tbody>{rows.map((row) => <tr key={ row.accountId }>
						<td className="identifier-cell">{ row.accountId }</td><td>{ row.status }</td>
						<td className="currency-cell">{ currency.format(row.currentBalance) }</td>
						<td className="currency-cell">{ currency.format(row.creditLimit) }</td>
						<td><span className="table-action-group">
							<a className="table-action-link" href={"/accounts?accountId=" + encodeURIComponent(row.accountId)}>View</a>
							<a className="table-action-link" href={"/accounts/update?accountId=" + encodeURIComponent(row.accountId)}>Update</a>
						</span></td>
					</tr>)}</tbody>
				</table>
			</div></div>
			<PageNavigation page={ listing } />
			<nav aria-label="Related pages"><a href="/menu">Menu</a></nav>
		</main>
	);
}
