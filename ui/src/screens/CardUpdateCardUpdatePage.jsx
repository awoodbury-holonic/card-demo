import React, { useEffect, useState } from "react";

export default function CardUpdateCardUpdatePage()
{
	const initialCardKey = new URLSearchParams(window.location.search)
		.get("cardNumber") ?? "";
	const [cardKey, setCardKey] = useState(initialCardKey);
	const [enteredCardKey, setEnteredCardKey] = useState(initialCardKey);
	const [expected, setExpected] = useState(null);
	const [proposed, setProposed] = useState(null);
	const [outcome, setOutcome] = useState("");
	const [loading, setLoading] = useState(false);

	useEffect(() =>
	{
		if (!cardKey) return;
		setLoading(true);
		setExpected(null);
		setProposed(null);
		setOutcome("");
		fetch("/api/cards/" + encodeURIComponent(cardKey) +
			"/update-snapshot", { credentials: "same-origin" })
			.then(response => response.ok ? response.json() : Promise.reject())
			.then(snapshot =>
			{
				setExpected(snapshot);
				setProposed(snapshot);
			})
			.catch(() => setOutcome("Card could not be loaded."))
			.finally(() => setLoading(false));
	}, [cardKey]);

	function findCard(event)
	{
		event.preventDefault();
		const value = enteredCardKey.trim();
		const decimal = value.length === 16 && [...value].every(
			(digit) => digit >= "0" && digit <= "9");
		if (!decimal)
		{
			setOutcome("Card number must be a 16-digit number.");
			return;
		}
		const query = new URLSearchParams(window.location.search);
		query.set("cardNumber", value);
		window.history.replaceState(null, "", window.location.pathname + "?" +
			query.toString());
		setCardKey(value);
	}

	async function save(event)
	{
		event.preventDefault();
		setLoading(true);
		setOutcome("");
		try
		{
			const response = await fetch("/api/cards/" + encodeURIComponent(cardKey),
				{ method: "PUT", credentials: "same-origin",
					headers: { "Content-Type": "application/json; charset=UTF-8" },
					body: JSON.stringify({ expected, proposed }) });
			const result = await response.json().catch(() => null);
			setOutcome(result?.message || result?.outcome || "Card could not be saved. Check the entered values.");
			if (result?.outcome === "UPDATED" && result.current)
			{
				setExpected(result.current);
				setProposed(result.current);
			}
		}
		catch { setOutcome("Card could not be saved. Please try again."); }
		finally { setLoading(false); }
	}

	if (!cardKey || proposed == null)
		return <main>
			<form onSubmit={findCard} autoComplete="off">
				<label htmlFor="CardUpdateCardUpdatePage-key">Card number</label>
				<input id="CardUpdateCardUpdatePage-key" name="cardNumber"
					inputMode="numeric" maxLength={16}
					value={enteredCardKey} required
					onChange={event => setEnteredCardKey(event.target.value)} />
				<button type="submit" disabled={loading}>
					{loading ? "Loading…" : "Inquire"}
				</button>
			</form>
			{outcome && <p role="alert">{outcome}</p>}
		</main>;

	return <main><form onSubmit={save} autoComplete="off">
		<label>Card number<input value={cardKey} readOnly /></label>
		<label>CVV<input type="number" min="0" max="999" step="1" required value={proposed.cvv}
			onChange={event => setProposed({ ...proposed,
				cvv: Number(event.target.value) })} /></label>
		<label>Embossed name<input maxLength={50} pattern="[A-Za-z ]+" required value={proposed.embossedName}
			onChange={event => setProposed({ ...proposed,
				embossedName: event.target.value })} /></label>
		<label>Expiration year<input type="number" min="1950" max="2099" step="1" required value={proposed.expirationYear}
			onChange={event => setProposed({ ...proposed,
				expirationYear: event.target.value })} /></label>
		<label>Expiration month<input type="number" min="1" max="12" step="1" required value={proposed.expirationMonth}
			onChange={event => setProposed({ ...proposed,
				expirationMonth: event.target.value })} /></label>
		<label>Expiration day<input type="number" min="1" max="31" step="1" required value={proposed.expirationDay}
			onChange={event => setProposed({ ...proposed,
				expirationDay: event.target.value })} /></label>
		<label>Active status<input maxLength={1} pattern="[YN]" required value={proposed.activeStatus}
			onChange={event => setProposed({ ...proposed,
				activeStatus: event.target.value })} /></label>
		<button type="submit" disabled={loading}>Save</button><output role="status">{outcome}</output>
	</form></main>;
}
