import { useEffect, useState } from "react";
import TextField, { useTextFields } from "../components/TextField.jsx";

const emptyValue = { userId: "", firstName: "", lastName: "", password: "", userType: "" };

export default function COUSR2A()
{
	const initialIdentifier = new URLSearchParams(window.location.search).get("userId") ?? "";
	const form = useTextFields({ ...emptyValue, userId: initialIdentifier });
	const [message, setMessage] = useState("");

	async function lookup(identifier = form.fields.userId)
	{
		try
		{
			const response = await fetch("/api/user-security/update?userId=" +
				encodeURIComponent(identifier), { credentials: "same-origin" });
			const result = response.ok ? await response.json() : null;
			if (result?.outcome === "FOUND" && result.value)
				form.load(result.value);
			setMessage(String(result?.message ?? "Unable to find user"));
		}
		catch { setMessage("Unable to find user"); }
	}

	useEffect(() =>
	{
		if (initialIdentifier) lookup(initialIdentifier);
	}, []);

	async function update(event)
	{
		event.preventDefault();
		try
		{
			const response = await fetch("/api/user-security/update", {
				method: "POST", credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(form.fields)
			});
			const result = response.ok ? await response.json() : null;
			if (result?.value) form.load(result.value);
			setMessage(String(result?.message ?? "Unable to update user"));
		}
		catch { setMessage("Unable to update user"); }
	}

	return (
		<main><h1>Update user</h1><form onSubmit={ update }>
			<TextField label="User ID" maxLength={8} { ...form.bind("userId") } />
			<button type="button" onClick={ () => lookup() }>Find user</button>
			<TextField label="First name" maxLength={20} { ...form.bind("firstName") } />
			<TextField label="Last name" maxLength={20} { ...form.bind("lastName") } />
			<TextField label="Password" type="password" maxLength={8} { ...form.bind("password") } />
			<TextField label="User type" maxLength={1} { ...form.bind("userType") } />
			{message && <p role="status">{ message }</p>}
			<button type="submit">Update user</button>
		</form></main>
	);
}
