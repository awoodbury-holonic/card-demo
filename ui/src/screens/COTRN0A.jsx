import { useState } from "react";
import PageNavigation, { usePagedRecords } from "../components/PagedRecords.jsx";

const dataset = { endpoint: "/api/transactions", rowsProperty: "rows", hasNextProperty: "hasNext",
	nextKeyProperty: "lastKey", cursorParameter: "startKey",
	nextParameters: { "direction": "FORWARD", "anchorMode": "EXCLUDE_ANCHOR" } };

export default function COTRN0A()
{
	const [draftQuery, setDraftQuery] = useState("");
	const listing = usePagedRecords(dataset, { "startKey": "", "direction": "FORWARD", "anchorMode": "INCLUDE_ANCHOR" });
	const { rows, loading, error, setError } = listing;
	const rowActions = [{"href_template":"/transactions?operation=view&id={id}","label":"View","operation":"detail"}];
	const queryWidth = Number("16");
	const search = (event) =>
	{
		event.preventDefault();
		const entered = draftQuery.trim();
		if (queryWidth > 0 && entered && (entered.length > queryWidth
			|| ![...entered].every(digit => digit >= "0" && digit <= "9")))
		{
			setError("Enter a numeric identifier of up to " + queryWidth + " digits.");
			return;
		}
		const startKey = queryWidth > 0 && entered ? entered.padStart(queryWidth, "0") : entered;
		listing.search({ "startKey": startKey, "direction": "FORWARD", "anchorMode": "INCLUDE_ANCHOR" });
	};
	const rowKeyToken = String.fromCharCode(123) + "id" + String.fromCharCode(125);

	return (
		<main>
			<h1>Transactions</h1>
			<p>Browse and search transactions.</p>
			<form onSubmit={ search }>
				<label htmlFor="COTRN0A-query">Transaction ID</label>
				<input id="COTRN0A-query" value={ draftQuery } onChange={(event) => setDraftQuery(event.target.value)} />
				<button type="submit" disabled={ loading }>Search</button>
			</form>
			<section aria-label="Transactions">
				{error && <p role="alert">{ error }</p>}
				{loading && <p role="status">Loading…</p>}
				{!loading && !error && rows.length === 0 && <p role="status">No matching records.</p>}
				<div className="data-table-card">
					<div className="data-table-scroll">
				<table className="data-table">
					<thead><tr>
						<th scope="col">Transaction ID</th>
						<th scope="col">Date</th>
						<th scope="col">Description</th>
						<th scope="col">Amount</th>
						{rowActions.length > 0 && <th scope="col">Actions</th>}
					</tr></thead>
					<tbody>
						{rows.map((row) => (
							<tr key={String(row["id"])}>
								<td>{String(row["id"] ?? "")}</td>

								<td>{String(row["date"] ?? "")}</td>

								<td>{String(row["description"] ?? "")}</td>

								<td>{String(row["amount"] ?? "")}</td>

								{rowActions.length > 0 && <td><span className="table-action-group">
					{rowActions.map((action) => (
						<a className="table-action-link" key={ action.operation } href={ action.href_template.replace(rowKeyToken, encodeURIComponent(String(row["id"] ?? ""))) }>{ action.label }</a>
									))}
								</span></td>}
							</tr>
						))}
					</tbody>
				</table>
					</div>
				</div>
				<PageNavigation page={ listing } />
			</section>
		</main>
	);
}
