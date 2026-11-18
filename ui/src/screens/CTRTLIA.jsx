import PageNavigation, { usePagedRecords } from "../components/PagedRecords.jsx";

const dataset = { endpoint: "/api/transaction-types", rowsProperty: "rows", hasNextProperty: "hasNext",
	rowKeyProperty: "trType", cursorParameter: "startKey", nextParameters: { direction: "FORWARD" },
	failureMessage: "Transaction types could not be loaded." };

export default function CTRTLIA()
{
	const listing = usePagedRecords(dataset, { startKey: "", direction: "FORWARD" });
	const { rows, loading, error } = listing;

	return (
		<main>
			<h1>Transaction types</h1>
			<p>Browse and maintain transaction types.</p>
			{error && <p role="alert">{error}</p>}
			{loading && <p role="status">Loading…</p>}
			<div className="data-table-card">
				<div className="data-table-scroll">
			<table className="data-table">
				<thead><tr><th>Type</th><th>Description</th><th>Actions</th></tr></thead>
				<tbody>{rows.map((row) => <tr key={String(row["trType"])}>
					<td>{String(row["trType"] ?? "")}</td>
					<td>{String(row["trDescription"] ?? "")}</td>
					<td><a className="table-action-link" href={"/admin/transaction-types?operation=maintain&type=" + encodeURIComponent(String(row["trType"] ?? ""))}>Maintain</a></td>
				</tr>)}</tbody>
			</table>
				</div>
			</div>
			<PageNavigation page={ listing }>
				<a className="table-action-link" href="/admin/transaction-types?operation=maintain">Add transaction type</a>
			</PageNavigation>
		</main>
	);
}
