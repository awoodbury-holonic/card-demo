import { useState } from "react";

export default function COBIL0A()
{
	const [accountId, setAccountId] = useState("");
	const [result, setResult] = useState(null);
	const [message, setMessage] = useState("");
	const [loading, setLoading] = useState(false);

	async function submit(decision)
	{
		setLoading(true);
		setMessage("");
		try
		{
			const response = await fetch("/api/bill-payments", {
				method: "POST",
				credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ accountId: accountId.trim(), decision })
			});
			const value = response.ok ? await response.json() : null;
			setResult(value);
			setMessage(String(value?.outcome ?? "Payment service is unavailable."));
		}
		catch
		{
			setResult(null);
			setMessage("Payment service is unavailable.");
		}
		finally
		{
			setLoading(false);
		}
	}

	return (
		<main>
			<h1>Pay account balance</h1>
			<label>Account ID
				<input name="accountId" maxLength={11}
					value={ accountId }
					onChange={(event) => setAccountId(event.target.value)} />
			</label>
			<div>
				<button type="button" disabled={ loading }
					onClick={() => submit("PREVIEW")}>Preview payment</button>
				<button type="button" disabled={ loading }
					onClick={() => submit("CONFIRM")}>Pay full balance</button>
				<button type="button" disabled={ loading }
					onClick={() => submit("CANCEL")}>Cancel</button>
			</div>
			{message && <p role="status">{ message }</p>}
			{result?.previousBalance != null &&
				<p>Previous balance: { result.previousBalance }</p>}
			{result?.remainingBalance != null &&
				<p>Remaining balance: { result.remainingBalance }</p>}
			<a href="/menu">Back to menu</a>
		</main>
	);
}
