import { useState } from "react";
import PageNavigation, { usePagedRecords } from "../components/PagedRecords.jsx";

const dataset = { endpoint: "/api/users", rowsProperty: "rows", hasNextProperty: "hasNext",
	nextKeyProperty: "lastKey", cursorParameter: "startKey",
	nextParameters: { "direction": "FORWARD", "anchorMode": "EXCLUDE_ANCHOR" } };

export default function COUSR0A()
{
	const [draftQuery, setDraftQuery] = useState("");
	const listing = usePagedRecords(dataset, { "startKey": "", "direction": "FORWARD", "anchorMode": "INCLUDE_ANCHOR" });
	const { rows, loading, error, setError } = listing;
	const rowActions = [{"href_template":"/admin/users?operation=update&userId={userId}","label":"Edit","operation":"update"},{"href_template":"/admin/users?operation=delete&userId={userId}","label":"Delete","operation":"delete"}];
	const queryWidth = Number("0");
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
	const rowKeyToken = String.fromCharCode(123) + "userId" + String.fromCharCode(125);

	return (
		<main>
			<h1>Users</h1>
			<p>Search for CardDemo users.</p>
			<form onSubmit={ search }>
				<label htmlFor="COUSR0A-query">User ID starts with</label>
				<input id="COUSR0A-query" value={ draftQuery } onChange={(event) => setDraftQuery(event.target.value)} />
				<button type="submit" disabled={ loading }>Search</button>
			</form>
			<section aria-label="Users">
				{error && <p role="alert">{ error }</p>}
				{loading && <p role="status">Loading…</p>}
				{!loading && !error && rows.length === 0 && <p role="status">No matching records.</p>}
				<div className="data-table-card">
					<div className="data-table-scroll">
				<table className="data-table">
					<thead><tr>
						<th scope="col">User ID</th>
						<th scope="col">First name</th>
						<th scope="col">Last name</th>
						<th scope="col">Type</th>
						{rowActions.length > 0 && <th scope="col">Actions</th>}
					</tr></thead>
					<tbody>
						{rows.map((row) => (
							<tr key={String(row["userId"])}>
								<td>{String(row["userId"] ?? "")}</td>

								<td>{String(row["firstName"] ?? "")}</td>

								<td>{String(row["lastName"] ?? "")}</td>

								<td>{String(row["userType"] ?? "")}</td>

								{rowActions.length > 0 && <td><span className="table-action-group">
					{rowActions.map((action) => (
						<a className="table-action-link" key={ action.operation } href={ action.href_template.replace(rowKeyToken, encodeURIComponent(String(row["userId"] ?? ""))) }>{ action.label }</a>
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
