import { useState } from "react";

const detailFields = [{"property":"embossedName","label":"embossedName"},{"property":"activeStatus","label":"activeStatus"},{"property":"expirationMonth","label":"expirationMonth"},{"property":"expirationYear","label":"expirationYear"}];
const detailLabels = {'embossedName':'Embossed name','activeStatus':'Active status','expirationMonth':'Expiration month','expirationYear':'Expiration year'};

export default function CCRDSLA()
{
	const initialKey = new URLSearchParams(window.location.search).get("cardNumber") ?? "";
	const [key, setKey] = useState(initialKey);
	const [detail, setDetail] = useState(null);
	const [message, setMessage] = useState("");
	const [loading, setLoading] = useState(false);

	async function inquire(event)
	{
		event.preventDefault();
		const normalizedKey = key.trim();
		if (!normalizedKey)
		{
			setDetail(null);
			setMessage("Card number is required.");
			return;
		}
		setLoading(true);
		setMessage("");
		try
		{
			const response = await fetch("/api/cards/" +
				encodeURIComponent(normalizedKey), { credentials: "same-origin" });
			if (response.status === 404)
			{
				setDetail(null);
				setMessage("The credit card was not found.");
				return;
			}
			if (!response.ok) throw new Error("The credit card inquiry could not be completed.");
			setDetail(await response.json());
		}
		catch (failure)
		{
			setDetail(null);
			setMessage(failure.message || "The credit card inquiry could not be completed.");
		}
		finally
		{
			setLoading(false);
		}
	}

	function clear()
	{
		setKey("");
		setDetail(null);
		setMessage("");
	}

	return (
		<main>
			<h1>Credit card details</h1>
			<p>Find a credit card by card number.</p>
			<form onSubmit={ inquire }>
				<label>Card number
					<input name="CC-CARD-NUM"
						maxLength={16}
						value={ key }
						onChange={(event) => setKey(event.target.value)} />
				</label>
				<button type="submit" disabled={ loading }>Inquire</button>
				<button type="button" onClick={ clear }>Clear</button>
			</form>
			{message && <p role="status">{ message }</p>}
			{detail && <section className="detail-table-card">
				<div className="detail-table-scroll">
					<table className="detail-table" aria-label="Credit card details">
						<tbody>
							{detailFields.map((field) => (
								<tr key={ field.property }>
									<th scope="row">{detailLabels[field.property] ?? field.label}</th>
									<td>{String(detail[field.property] ?? "")}</td>
								</tr>
							))}
						</tbody>
					</table>
				</div>
			</section>}
			<nav aria-label="Related pages">
				<a href="/cards">Credit cards</a>
				<a href="/menu">Menu</a>
			</nav>
		</main>
	);
}
