import { useEffect, useState } from "react";

const accountFields = [{"property":"acctActiveStatus","label":"Acct active status"},{"property":"acctCurrBal","label":"Acct curr bal"},{"property":"acctCreditLimit","label":"Acct credit limit"},{"property":"acctCashCreditLimit","label":"Acct cash credit limit"},{"property":"acctCurrCycCredit","label":"Acct curr cyc credit"},{"property":"acctCurrCycDebit","label":"Acct curr cyc debit"},{"property":"acctOpenDate","label":"Acct open date"},{"property":"acctExpiraionDate","label":"Acct expiraion date"},{"property":"acctReissueDate","label":"Acct reissue date"},{"property":"acctGroupId","label":"Acct group id"}];
const customerFields = [{"property":"custId","label":"Cust id","sourceIdentifier":true,"sourceWidth":9},{"property":"custFicoCreditScore","label":"Cust fico credit score"},{"property":"custDobYyyyMmDd","label":"Cust dob yyyy mm dd"},{"property":"custFirstName","label":"Cust first name"},{"property":"custMiddleName","label":"Cust middle name"},{"property":"custLastName","label":"Cust last name"},{"property":"custAddrLine1","label":"Cust addr line 1"},{"property":"custAddrLine2","label":"Cust addr line 2"},{"property":"custAddrLine3","label":"Cust addr line 3"},{"property":"custAddrStateCd","label":"Cust addr state cd"},{"property":"custAddrZip","label":"Cust addr zip"},{"property":"custAddrCountryCd","label":"Cust addr country cd"},{"property":"custPhoneNum1","label":"Cust phone num 1"},{"property":"custPhoneNum2","label":"Cust phone num 2"},{"property":"custGovtIssuedId","label":"Cust govt issued id"},{"property":"custEftAccountId","label":"Cust eft account id"},{"property":"custPriCardHolderInd","label":"Cust pri card holder ind"}];
const fieldLabels = {'acctActiveStatus':'Status','acctCurrBal':'Current balance','acctCreditLimit':'Credit limit','acctCashCreditLimit':'Cash credit limit','acctCurrCycCredit':'Current-cycle credit','acctCurrCycDebit':'Current-cycle debit','acctOpenDate':'Open date','acctExpiraionDate':'Expiration date','acctReissueDate':'Reissue date','acctGroupId':'Group ID','custId':'Customer ID','custFicoCreditScore':'FICO score','custDobYyyyMmDd':'Date of birth','custFirstName':'First name','custMiddleName':'Middle name','custLastName':'Last name','custAddrLine1':'Address line 1','custAddrLine2':'Address line 2','custAddrLine3':'City','custAddrStateCd':'State','custAddrZip':'Postal code','custAddrCountryCd':'Country','custPhoneNum1':'Primary phone','custPhoneNum2':'Secondary phone','custGovtIssuedId':'Government ID','custEftAccountId':'EFT account ID','custPriCardHolderInd':'Primary cardholder'};
const currencyFields = new Set(["acctCurrBal","acctCreditLimit","acctCashCreditLimit","acctCurrCycCredit","acctCurrCycDebit"]);
const dateFields = new Set(["acctOpenDate","acctExpiraionDate","acctReissueDate","custDobYyyyMmDd"]);
const currencyFormatter = new Intl.NumberFormat("en-US",
	{ style: "currency", currency: "USD" });
const dateFormatter = new Intl.DateTimeFormat("en-US",
	{ dateStyle: "medium", timeZone: "UTC" });

function displayValue(field, value)
{
	const cellValue = value[field.property];
	if (cellValue == null || cellValue === "") return "";
	if (field.sourceIdentifier)
	{
		const digits = String(cellValue);
		const decimal = digits.length > 0 && [...digits].every(
			(digit) => digit >= "0" && digit <= "9");
		return decimal ? digits.padStart(field.sourceWidth, "0") : digits;
	}
	if (currencyFields.has(field.property))
	{
		const amount = Number(cellValue);
		return Number.isFinite(amount) ? currencyFormatter.format(amount) : String(cellValue);
	}
	if (dateFields.has(field.property))
	{
		const date = new Date(String(cellValue) + "T00:00:00Z");
		return Number.isNaN(date.getTime()) ? String(cellValue) : dateFormatter.format(date);
	}
	return String(cellValue);
}

function DetailSection({ title, fields, value, leadingIdentifier })
{
	if (!value) return null;
	return (
		<section className="detail-table-card">
			<h2>{ title }</h2>
			<div className="detail-table-scroll">
				<table className="detail-table" aria-label={ title }>
					<tbody>
						{leadingIdentifier && <tr>
							<th scope="row">{leadingIdentifier.label}</th>
							<td className="identifier-cell">{leadingIdentifier.value}</td>
						</tr>}
						{fields.map((field) => (
							<tr key={ field.property }>
								<th scope="row">{fieldLabels[field.property] ?? field.label}</th>
								<td className={field.sourceIdentifier
									? "identifier-cell" : currencyFields.has(field.property)
									? "currency-cell" : dateFields.has(field.property)
										? "date-cell" : undefined}>{displayValue(field, value)}</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>
		</section>
	);
}

export default function CACTVWA()
{
	const [key, setKey] = useState(() =>
		new URLSearchParams(window.location.search).get("accountId") ?? "");
	const [result, setResult] = useState(null);
	const [message, setMessage] = useState("");
	const [loading, setLoading] = useState(false);

	useEffect(() =>
	{
		if (new URLSearchParams(window.location.search).has("accountId")) inquire();
	}, []);

	const inquire = async (event) =>
	{
		event?.preventDefault();
		const enteredKey = key.trim();
		if (!enteredKey)
		{
			setResult(null);
			setMessage("Account ID is required.");
			return;
		}
		const decimalKey = [...enteredKey].every(
			(digit) => digit >= "0" && digit <= "9");
		const normalizedKey = decimalKey
			? enteredKey.padStart(11, "0") : enteredKey;
		setKey(normalizedKey);
		setLoading(true);
		setMessage("");
		try
		{
			const response = await fetch("/api/accounts/" + encodeURIComponent(normalizedKey),
				{ credentials: "same-origin" });
			if (response.status === 404)
			{
				setResult(null);
				setMessage("Account or customer was not found.");
				return;
			}
			if (!response.ok) throw new Error("The account could not be loaded.");
			const payload = await response.json();
			setResult({ ...payload, accountId: normalizedKey });
		}
		catch (failure)
		{
			setResult(null);
			setMessage(failure.message || "The account could not be loaded.");
		}
		finally
		{
			setLoading(false);
		}
	};

	return (
		<main>
			<h1>Account details</h1>
			<p>Enter or update id of account to display</p>
			<form onSubmit={ inquire }>
				<label htmlFor="CACTVWA-key">Account ID</label>
				<input id="CACTVWA-key" name="ACCTSID"
					inputMode="numeric" maxLength={11} value={ key }
					onChange={(event) => setKey(event.target.value)} />
				<button type="submit" disabled={ loading }>
					{loading ? "Loading…" : "Inquire"}
				</button>
			</form>
			{message && <p role="alert">{ message }</p>}
			<DetailSection title="Account" fields={ accountFields }
				value={ result?.account } leadingIdentifier={result && {
					label: "Account ID", value: result.accountId
				}} />
			<DetailSection title="Customer" fields={ customerFields }
				value={ result?.customer } />
			<nav aria-label="Related pages">
				<a href="/accounts/list">Back to Account List</a>
				<a href="/menu">Menu</a>
			</nav>
		</main>
	);
}
