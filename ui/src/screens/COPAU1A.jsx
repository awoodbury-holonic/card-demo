import { useEffect, useState } from "react";

// The pending-authorization detail screen: one authorization, chosen on the
// summary screen. Mark fraud toggles it between fraud confirmed and fraud
// removed; Next shows the account's next authorization; Back returns to the
// summary for the same account.
export default function COPAU1A()
{
	const query = new URLSearchParams(window.location.search);
	const accountId = query.get("accountId") || "";
	const [key, setKey] = useState(query.get("key") || "");
	const [detail, setDetail] = useState(null);
	const [message, setMessage] = useState("");
	const [busy, setBusy] = useState(false);

	function show(result)
	{
		setDetail(result);
		setMessage(result.message || "");
		if (result.key && result.key !== key)
		{
			setKey(result.key);
			const url = new URL(window.location.href);
			url.searchParams.set("key", result.key);
			window.history.replaceState(null, "", url);
		}
	}

	async function call(input, init)
	{
		setBusy(true);
		try
		{
			const response = await fetch(input, { credentials: "same-origin", ...init });
			if (!response.ok) throw new Error("Unable to read the authorization.");
			show(await response.json());
		}
		catch (failure)
		{
			setMessage(failure.message || "Unable to read the authorization.");
		}
		finally
		{
			setBusy(false);
		}
	}

	function get(next)
	{
		const params = new URLSearchParams({ accountId, key, next: String(next) });
		return call("/api/authorizations/pending/detail?" + params);
	}

	useEffect(() => { get(false); }, []);

	function markFraud()
	{
		call("/api/authorizations/pending/detail/fraud", { method: "POST", headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ accountId, key }) });
	}

	const fields = detail && !detail.error ? [
		["CARDNUM", "Card #:", detail.cardNumber], ["AUTHDT", "Auth Date:", detail.date],
		["AUTHTM", "Auth Time:", detail.time], ["AUTHRSP", "Auth Resp:", detail.response],
		["AUTHRSN", "Resp Reason:", detail.reason], ["AUTHCD", "Auth Code:", detail.processingCode],
		["AUTHAMT", "Amount:", detail.amount], ["POSEMD", "POS Entry Mode:", detail.posEntryMode],
		["AUTHSRC", "Source:", detail.source], ["MCCCD", "MCC Code:", detail.merchantCategory],
		["CRDEXP", "Card Exp. Date:", detail.cardExpiry], ["AUTHTYP", "Auth Type:", detail.type],
		["TRNID", "Tran Id:", detail.transactionId], ["AUTHMTC", "Match Status:", detail.matchStatus],
		["AUTHFRD", "Fraud Status:", detail.fraud], ["MERNAME", "Name:", detail.merchantName],
		["MERID", "Merchant ID:", detail.merchantId], ["MERCITY", "City:", detail.merchantCity],
		["MERST", "State:", detail.merchantState], ["MERZIP", "Zip:", detail.merchantZip]
	] : [];
	return (
		<section data-screen="COPAU1A">
			<h1>View Authorization Details</h1>
			<dl>
				{fields.map(([field, label, value]) => (
					<div key={field}><dt>{label}</dt><dd data-field={field}>{value}</dd></div>
				))}
			</dl>
			<p role="status">{message}</p>
			<nav>
				<button type="button" onClick={markFraud} disabled={busy || !fields.length}>Mark/Remove Fraud</button>
				<button type="button" onClick={() => get(true)} disabled={busy || !fields.length}>Next Auth</button>
				<a href={"/authorizations/pending?accountId=" + encodeURIComponent(accountId)}>Back</a>
			</nav>
		</section>
	);
}
