import { useState } from "react";

export default function COUSR1A()
{
	const [message, setMessage] = useState("");

	async function submit(event)
	{
		event.preventDefault();
		const values = new FormData(event.currentTarget);
		try
		{
			const response = await fetch("/api/user-security/create", {
				method: "POST",
				credentials: "same-origin",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify({
					userId: String(values.get("userId") ?? ""),
					firstName: String(values.get("firstName") ?? ""),
					lastName: String(values.get("lastName") ?? ""),
					password: String(values.get("password") ?? ""),
					userType: String(values.get("userType") ?? "")
				})
			});
			const result = response.ok ? await response.json() : null;
			setMessage(String(result?.message ?? "Unable to add user"));
		}
		catch
		{
			setMessage("Unable to add user");
		}
	}

	return (
		<main>
			<h1>Add user</h1>
			<form onSubmit={ submit }>
				<label>User ID<input name="userId" maxLength={8} /></label>
				<label>First name<input name="firstName" maxLength={20} /></label>
				<label>Last name<input name="lastName" maxLength={20} /></label>
				<label>Password<input name="password" type="password" maxLength={8} /></label>
				<label>User type<input name="userType" maxLength={1} /></label>
				{message && <p role="status">{ message }</p>}
				<button type="submit">Add user</button>
			</form>
		</main>
	);
}
