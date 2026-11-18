import { useEffect, useState } from "react";

const ACCOUNT_FIELDS = Object.freeze([{"name":"accountId","source":"$key","label":"Account ID","type":"number","readOnly":true},{"name":"status","source":"acctActiveStatus","label":"Status"},{"name":"currentBalance","source":"acctCurrBal","label":"Current balance","type":"number"},{"name":"creditLimit","source":"acctCreditLimit","label":"Credit limit","type":"number"},{"name":"cashCreditLimit","source":"acctCashCreditLimit","label":"Cash credit limit","type":"number"},{"name":"openedOn","source":"acctOpenDate","label":"Open date","type":"date"},{"name":"expiresOn","source":"acctExpiraionDate","label":"Expiration date","type":"date"},{"name":"reissueOn","source":"acctReissueDate","label":"Reissue date","type":"date"},{"name":"currentCycleCredits","source":"acctCurrCycCredit","label":"Current-cycle credits","type":"number"},{"name":"currentCycleDebits","source":"acctCurrCycDebit","label":"Current-cycle debits","type":"number"},{"name":"groupId","source":"acctGroupId","label":"Group ID"}]);
const CUSTOMER_FIELDS = Object.freeze([{"name":"customerId","source":"custId","label":"Customer ID","type":"number","readOnly":true},{"name":"firstName","source":"custFirstName","label":"First name"},{"name":"middleName","source":"custMiddleName","label":"Middle name"},{"name":"lastName","source":"custLastName","label":"Last name"},{"name":"addressLine1","source":"custAddrLine1","label":"Address line 1"},{"name":"addressLine2","source":"custAddrLine2","label":"Address line 2"},{"name":"city","source":"custAddrLine3","label":"City"},{"name":"state","source":"custAddrStateCd","label":"State"},{"name":"country","source":"custAddrCountryCd","label":"Country"},{"name":"postalCode","source":"custAddrZip","label":"Postal code"},{"name":"phoneNumber1","source":"custPhoneNum1","label":"Primary phone"},{"name":"phoneNumber2","source":"custPhoneNum2","label":"Secondary phone"},{"name":"socialSecurityNumber","source":"formattedCustSsn","label":"Social security number","transform":"digits"},{"name":"governmentIssuedId","source":"custGovtIssuedId","label":"Government ID"},{"name":"dateOfBirth","source":"custDobYyyyMmDd","label":"Date of birth","type":"date"},{"name":"eftAccountId","source":"custEftAccountId","label":"EFT account ID"},{"name":"primaryCardHolder","source":"custPriCardHolderInd","label":"Primary cardholder","type":"checkbox","transform":"yn"},{"name":"ficoCreditScore","source":"custFicoCreditScore","label":"FICO score","type":"number"}]);

function readValue(field, source, key)
{
	let value = field.source === "$key" ? key : source?.[field.source];
	if (field.transform === "digits") value = String(value ?? "").replace(/\D/g, "");
	if (field.transform === "yn") value = String(value ?? "").toUpperCase() === "Y";
	if (field.type === "number" && value !== "") value = Number(value);
	return value ?? "";
}

function project(fields, source, key)
{
	return Object.fromEntries(fields.map(field =>
		[field.name, readValue(field, source, key)]));
}

function Field({ field, value, onChange })
{
	return (
		<label>{field.label}
			<input name={field.name} type={field.type || "text"}
				value={field.type === "checkbox" ? undefined : value}
				checked={field.type === "checkbox" ? Boolean(value) : undefined}
				readOnly={Boolean(field.readOnly)}
				onChange={event => onChange(field.name,
					field.type === "checkbox" ? event.target.checked
						: field.type === "number" && event.target.value !== ""
							? Number(event.target.value) : event.target.value)} />
		</label>
	);
}

export default function CACTUPA()
{
	const [key, setKey] = useState(() =>
		new URLSearchParams(window.location.search).get("accountId") ?? "");
	const [prior, setPrior] = useState(null);
	const [requested, setRequested] = useState(null);
	const [phase, setPhase] = useState("EDIT");
	const [message, setMessage] = useState("");
	const [busy, setBusy] = useState(false);

	useEffect(() =>
	{
		if (new URLSearchParams(window.location.search).has("accountId")) load();
	}, []);

	async function load(event)
	{
		event?.preventDefault();
		const enteredKey = key.trim();
		const decimalKey = enteredKey.length > 0 && [...enteredKey].every(
			(digit) => digit >= "0" && digit <= "9");
		const normalizedKey = decimalKey
			? enteredKey.padStart(11, "0") : enteredKey;
		setKey(normalizedKey);
		setBusy(true);
		setMessage("");
		try
		{
			const response = await fetch("/api/accounts/" +
				encodeURIComponent(normalizedKey), { credentials: "same-origin" });
			if (!response.ok) throw new Error("load");
			const value = await response.json();
			const aggregate = {
				account: project(ACCOUNT_FIELDS, value.account, normalizedKey),
				customer: project(CUSTOMER_FIELDS, value.customer, normalizedKey)
			};
			setPrior(aggregate);
			setRequested({account: {...aggregate.account}, customer: {...aggregate.customer}});
			setPhase("EDIT");
		}
		catch
		{
			setPrior(null);
			setRequested(null);
			setMessage("The account could not be loaded.");
		}
		finally
		{
			setBusy(false);
		}
	}

	function change(section, name, value)
	{
		setRequested(current => ({...current,
			[section]: {...current[section], [name]: value}}));
		setPhase("EDIT");
	}

	async function save()
	{
		if (phase !== "CONFIRM")
		{
			setPhase("CONFIRM");
			setMessage("Review the changes, then save.");
			return;
		}
		setBusy(true);
		setMessage("");
		try
		{
			const response = await fetch("/api/accounts", {
				method: "PUT", credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({prior, requested})
			});
			const result = response.ok ? await response.json() : null;
			setMessage(String(result?.outcome ?? "The account could not be updated."));
			if (result?.outcome === "UPDATED" && result.value)
			{
				setPrior(result.value);
				setRequested(result.value);
				setPhase("EDIT");
			}
		}
		catch
		{
			setMessage("The service is unavailable.");
		}
		finally
		{
			setBusy(false);
		}
	}

	return (
		<main data-screen="CACTUPA">
			<h1>Update account</h1>
			<form className="lookup-form" onSubmit={load}>
				<label>Account ID<input inputMode="numeric"
					maxLength={11} value={key}
					onChange={event => setKey(event.target.value)} required /></label>
				<button type="submit" disabled={busy}>Load</button>
			</form>
			{requested && <form className="record-update-form"
				onSubmit={event => { event.preventDefault(); save(); }}>
				<fieldset className="form-section"><legend>Account</legend>
					<div className="form-grid">
						{ACCOUNT_FIELDS.map(field => <Field key={field.name} field={field}
							value={requested.account[field.name]}
							onChange={(name, value) => change("account", name, value)} />)}
					</div>
				</fieldset>
				<fieldset className="form-section"><legend>Customer</legend>
					<div className="form-grid">
						{CUSTOMER_FIELDS.map(field => <Field key={field.name} field={field}
							value={requested.customer[field.name]}
							onChange={(name, value) => change("customer", name, value)} />)}
					</div>
				</fieldset>
				<div className="form-actions">
					<button type="submit" disabled={busy}>
						{phase === "CONFIRM" ? "Save changes" : "Review changes"}
					</button>
					<button type="button" className="secondary-button"
						onClick={() => { setRequested(prior); setPhase("EDIT"); }}>
						Cancel changes
					</button>
				</div>
			</form>}
			{message && <p role="status">{message}</p>}
			<a href="/menu">Back to menu</a>
		</main>
	);
}
