import { useState } from "react";
import PageNavigation, { usePagedRecords } from "../components/PagedRecords.jsx";

const dataset = { endpoint: "{param_endpoint}", rowsProperty: "rows", hasNextProperty: "hasNext",
	nextKeyProperty: "lastKey", cursorParameter: "startKey",
	nextParameters: { direction: "FORWARD", anchorMode: "EXCLUDE_ANCHOR" } };

export default function CardListListPage()
{
	const [accountFilter, setAccountFilter] = useState("");
	const [cardFilter, setCardFilter] = useState("");
	const listing = usePagedRecords(dataset, { accountFilter: "", cardFilter: "", startKey: "", direction: "FORWARD", anchorMode: "INCLUDE_ANCHOR" });
	const { rows, loading, error } = listing;
	const search = (event) =>
	{
		event.preventDefault();
		listing.search({ accountFilter: accountFilter.trim(), cardFilter: cardFilter.trim(), startKey: "", direction: "FORWARD", anchorMode: "INCLUDE_ANCHOR" });
	};

	return (
		<main>
			<h1>{param_page_title}</h1>
			<p>{param_page_purpose}</p>
			<form onSubmit={search}>
				<label htmlFor="CardListListPage-account">{param_account_label}</label>
				<input id="CardListListPage-account" value={accountFilter} maxLength="{param_account_width}" inputMode="numeric" onChange={(event) => setAccountFilter(event.target.value)} />
				<label htmlFor="CardListListPage-card">{param_card_label}</label>
				<input id="CardListListPage-card" value={cardFilter} maxLength="{param_card_width}" inputMode="numeric" onChange={(event) => setCardFilter(event.target.value)} />
				<button type="submit" disabled={ loading }>Search</button>
			</form>
			{error && <p role="alert">{error}</p>}
			<div className="data-table-card">
				<div className="data-table-scroll">
			<table className="data-table">
				<thead><tr><th>Account ID</th><th>Card number</th><th>Status</th><th>Actions</th></tr></thead>
				<tbody>{rows.map((row) => <tr key={row.cardNumber}><td>{row.accountId}</td><td>{row.cardNumber}</td><td>{row.activeStatus}</td><td><span className="table-action-group"><a className="table-action-link" href={"{param_detail_path}" + encodeURIComponent(row.cardNumber)}>Details</a><a className="table-action-link" href={"{param_update_path}" + encodeURIComponent(row.cardNumber)}>Update</a></span></td></tr>)}</tbody>
			</table>
				</div>
			</div>
			<PageNavigation page={ listing } />
		</main>
	);
}
