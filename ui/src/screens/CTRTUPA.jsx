import { useEffect, useState } from "react";

export default function CTRTUPA()
{
	const initialKey = new URLSearchParams(window.location.search).get("type") ?? "";
	const [key, setKey] = useState(initialKey);
	const [value, setValue] = useState("");
	const [message, setMessage] = useState("");
	const [busy, setBusy] = useState(false);

	async function execute(action, commandKey = key, commandValue = value)
	{
		setBusy(true);
		setMessage("");
		try
		{
			const response = await fetch("/api/transaction-types/maintenance", {
				method: "POST", credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ action, value: {
					trType: commandKey,
					trDescription: commandValue
				} })
			});
			if (!response.ok) throw new Error("The transaction type could not be processed.");
			const result = await response.json();
			if (result.outcome === "FOUND") setValue(String(result.value?.["trDescription"] ?? ""));
			setMessage(String(result.outcome ?? "FAILED"));
		}
		catch
		{
			setMessage("The transaction type could not be processed.");
		}
		finally
		{
			setBusy(false);
		}
	}

	useEffect(() =>
	{
		if (initialKey) execute("READ", initialKey, "");
	}, []);

	return (
		<main>
			<h1>Maintain transaction type</h1>
			<label>Type<input value={key} maxLength={2} onChange={(event) => setKey(event.target.value)} /></label>
			<label>Description<input value={value} maxLength={50} onChange={(event) => setValue(event.target.value)} /></label>
			<div>
				<button type="button" disabled={busy} onClick={() => execute("READ")}>Read</button>
				<button type="button" disabled={busy} onClick={() => execute("INSERT")}>Add</button>
				<button type="button" disabled={busy} onClick={() => execute("UPDATE")}>Save</button>
				<button type="button" disabled={busy} onClick={() => execute("DELETE")}>Delete</button>
			</div>
			{message && <p role="status">{message}</p>}
			<a href="/admin/transaction-types">Back to transaction types</a>
		</main>
	);
}
