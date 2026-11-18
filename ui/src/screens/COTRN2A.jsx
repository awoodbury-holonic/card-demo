import { useState } from "react";

const INITIAL_FORM = Object.freeze({
	accountId: "", cardNumber: "", typeCode: "", categoryCode: "",
	source: "", description: "", amount: "", originalDate: "",
	processedDate: "", merchantId: "", merchantName: "",
	merchantCity: "", merchantZip: ""
});

const FIELDS = Object.freeze([
	["accountId", "Account ID", "number",
		{ min: 0, max: 99999999999 }, ""],
	["cardNumber", "Card number", "text",
		{ minLength: 16, maxLength: 16 }, ""],
	["typeCode", "Type code", "text",
		{ inputMode: "numeric", pattern: "[0-9]{1,2}",
			maxLength: 2 }, "Type code must contain at most 2 digits."],
	["categoryCode", "Category code", "number",
		{ min: 0, max: 9999 }, ""],
	["source", "Source", "text",
		{ maxLength: 10 }, ""],
	["description", "Description", "text",
		{ maxLength: 60 }, ""],
	["amount", "Amount", "number",
		{ step: "0.01",
			min: -999999999.99, max: 999999999.99 }, ""],
	["originalDate", "Original date", "date", {}, ""],
	["processedDate", "Processed date", "date", {}, ""],
	["merchantId", "Merchant ID", "number",
		{ min: 0, max: 999999999 }, ""],
	["merchantName", "Merchant name", "text",
		{ maxLength: 30 }, ""],
	["merchantCity", "Merchant city", "text",
		{ maxLength: 25 }, ""],
	["merchantZip", "Merchant postal code", "text",
		{ maxLength: 10 }, ""]
]);

export default function COTRN2A()
{
	const [form, setForm] = useState(INITIAL_FORM);
	const [message, setMessage] = useState("");
	const [transactionId, setTransactionId] = useState("");
	const [busy, setBusy] = useState(false);

	function change(event)
	{
		setForm({...form, [event.target.name]: event.target.value});
	}

	async function submit(event)
	{
		event.preventDefault();
		setBusy(true);
		setMessage("");
		setTransactionId("");
		try
		{
			const response = await fetch("/api/transactions", {
				method: "POST", credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({
					...form,
					accountId: Number(form.accountId),
					categoryCode: Number(form.categoryCode),
					merchantId: Number(form.merchantId),
					amount: form.amount
				})
			});
			const result = await response.json();
			setMessage(result.message || "Unable to add transaction.");
			setTransactionId(result.transactionId || "");
		}
		catch (failure)
		{
			setMessage("Transaction service is unavailable.");
		}
		finally
		{
			setBusy(false);
		}
	}

	function clear()
	{
		setForm(INITIAL_FORM);
		setMessage("");
		setTransactionId("");
	}

	return (
		<main data-screen="COTRN2A">
			<h1>Add transaction</h1>
			<form onSubmit={submit}>
				{FIELDS.map(([name, label, type, constraints, validationMessage]) => (
					<label key={name}>{label}
						<input name={name} type={type} value={form[name]}
							onChange={change}
							onInput={event => event.currentTarget.setCustomValidity("")}
							onInvalid={event => event.currentTarget.setCustomValidity(
								validationMessage)} required {...constraints} />
					</label>
				))}
				<button type="submit" disabled={busy}>
					{busy ? "Adding transaction..." : "Add transaction"}
				</button>
				<button type="button" onClick={clear}>Clear</button>
			</form>
			{transactionId && <p>Transaction ID: {transactionId}</p>}
			{message && <p role="status">{message}</p>}
			<a href="/transactions">Back to transactions</a>
		</main>
	);
}
