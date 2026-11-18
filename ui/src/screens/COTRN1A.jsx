import { useState } from "react";

const detailFields = [{"property":"transactionId","label":"transactionId"},{"property":"typeCode","label":"typeCode"},{"property":"categoryCode","label":"categoryCode"},{"property":"source","label":"source"},{"property":"description","label":"description"},{"property":"amount","label":"amount"},{"property":"merchantId","label":"merchantId"},{"property":"merchantName","label":"merchantName"},{"property":"merchantCity","label":"merchantCity"},{"property":"merchantZip","label":"merchantZip"},{"property":"cardNumber","label":"cardNumber"},{"property":"originalTimestamp","label":"originalTimestamp"},{"property":"processedTimestamp","label":"processedTimestamp"}];
const detailLabels = {'transactionId':'Transaction ID','typeCode':'Type code','categoryCode':'Category code','source':'Source','description':'Description','amount':'Amount','merchantId':'Merchant ID','merchantName':'Merchant name','merchantCity':'Merchant city','merchantZip':'Merchant postal code','cardNumber':'Card number','originalTimestamp':'Original timestamp','processedTimestamp':'Processed timestamp'};
const currencyFields = new Set(['amount']);
const dateFields = new Set(['originalTimestamp','processedTimestamp']);
const identifierFields = new Set(['transactionId','cardNumber']);
const currencyFormatter = new Intl.NumberFormat("en-US",
	{ style: "currency", currency: "USD" });
const dateFormatter = new Intl.DateTimeFormat("en-US",
	{ dateStyle: "medium", timeZone: "UTC" });

function displayValue(field, value)
{
	const cellValue = value[field.property];
	if (cellValue == null || cellValue === "") return "";
	if (currencyFields.has(field.property))
	{
		const amount = Number(cellValue);
		return Number.isFinite(amount) ? currencyFormatter.format(amount) : String(cellValue);
	}
	if (dateFields.has(field.property))
	{
		const source = String(cellValue);
		// The date part of the source text, as UTC: a timestamp (yyyy-MM-dd
		// HH:mm:ss...) has no zone, and parsing it whole reads it as local time,
		// which east of UTC shows the day before.
		const day = /^\d\d\d\d-\d\d-\d\d/.test(source) ? source.slice(0, 10) : "";
		const date = new Date(day ? day + "T00:00:00Z" : source);
		return Number.isNaN(date.getTime()) ? source : dateFormatter.format(date);
	}
	return String(cellValue);
}

export default function COTRN1A()
{
	const initialKey = new URLSearchParams(window.location.search).get("id") ?? "";
	const [key, setKey] = useState(initialKey);
	const [detail, setDetail] = useState(null);
	const [message, setMessage] = useState("");
	const [loading, setLoading] = useState(false);

	const inquire = async (event) =>
	{
		event.preventDefault();
		const normalizedKey = key.trim();
		if (!normalizedKey)
		{
			setDetail(null);
			setMessage("Transaction ID is required.");
			return;
		}
		setLoading(true);
		setMessage("");
		try
		{
			const response = await fetch("/api/transactions/" + encodeURIComponent(normalizedKey),
				{ credentials: "same-origin" });
			if (response.status === 404)
			{
				setDetail(null);
				setMessage("Transaction was not found.");
				return;
			}
			if (!response.ok) throw new Error("The transaction could not be loaded.");
			setDetail(await response.json());
		}
		catch (failure)
		{
			setDetail(null);
			setMessage(failure.message || "The transaction could not be loaded.");
		}
		finally
		{
			setLoading(false);
		}
	};

	const clear = () =>
	{
		setKey("");
		setDetail(null);
		setMessage("");
	};

	return (
		<main>
			<h1>Transaction details</h1>
			<p>Find a transaction by its identifier.</p>
			<form onSubmit={ inquire }>
				<label htmlFor="COTRN1A-key">Transaction ID</label>
				<input id="COTRN1A-key" name="TRNIDINI"
					maxLength={16} value={ key }
					onChange={(event) => setKey(event.target.value)} />
				<button type="submit" disabled={ loading }>
					{loading ? "Loading…" : "Inquire"}
				</button>
				<button type="button" onClick={ clear }>Clear</button>
			</form>
			{message && <p role="alert">{ message }</p>}
			{detail && <section className="detail-table-card">
				<div className="detail-table-scroll">
					<table className="detail-table" aria-label="Transaction details">
						<tbody>
							{detailFields.map((field) => (
								<tr key={ field.property }>
									<th scope="row">{detailLabels[field.property] ?? field.label}</th>
									<td className={currencyFields.has(field.property)
										? "currency-cell" : dateFields.has(field.property)
											? "date-cell" : identifierFields.has(field.property)
												? "identifier-cell" : undefined}>
										{displayValue(field, detail)}
									</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			</section>}
			<nav aria-label="Related pages">
				<a href="/transactions">Transactions</a>
				<a href="/menu">Menu</a>
			</nav>
		</main>
	);
}
