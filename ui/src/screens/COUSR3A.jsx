import { useState } from "react";

const emptyValue = { userId: "", firstName: "", lastName: "", userType: "" };

export default function COUSR3A()
{
	const initialIdentifier = new URLSearchParams(window.location.search).get("userId") ?? "";
	const [value, setValue] = useState({ ...emptyValue, userId: initialIdentifier });
	const [message, setMessage] = useState("");

	async function lookup()
	{
		try
		{
			const response = await fetch("/api/user-security/delete?userId=" +
				encodeURIComponent(value.userId), { credentials: "same-origin" });
			const result = response.ok ? await response.json() : null;
			if (result?.outcome === "FOUND" && result.value) setValue(result.value);
			setMessage(String(result?.message ?? "Unable to find user"));
		}
		catch { setMessage("Unable to find user"); }
	}

	async function remove(event)
	{
		event.preventDefault();
		try
		{
			const response = await fetch("/api/user-security/delete", {
				method: "POST", credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({ userId: value.userId })
			});
			const result = response.ok ? await response.json() : null;
			setMessage(String(result?.message ?? "Unable to delete user"));
			if (result?.outcome === "DELETED") setValue(emptyValue);
		}
		catch { setMessage("Unable to delete user"); }
	}

	return (
		<main><h1>Delete user</h1><form onSubmit={ remove }>
			<label>User ID<input value={ value.userId } maxLength={8} onChange={ event => setValue({ ...value, userId: event.target.value }) } /></label>
			<button type="button" onClick={ lookup }>Find user</button>
			<p>First name: { value.firstName }</p>
			<p>Last name: { value.lastName }</p>
			<p>User type: { value.userType }</p>
			{message && <p role="status">{ message }</p>}
			<button type="submit">Delete user</button>
		</form></main>
	);
}
