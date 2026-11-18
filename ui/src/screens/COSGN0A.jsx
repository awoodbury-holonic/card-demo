import { useState } from "react";
import { post } from "../api.js";

export default function COSGN0A()
{
	const [message, setMessage] = useState("");
	const [focus, setFocus] = useState("identifier");

	async function submit(event)
	{
		event.preventDefault();
		const values = new FormData(event.currentTarget);
		try
		{
			const result = await post("/api/authentication", {
				identifier: String(values.get("identifier") ?? ""),
				credential: String(values.get("credential") ?? "")
			});
			setMessage(String(result.message ?? ""));
			if (result.outcome === "AUTHENTICATED" && result.destination)
			{
				window.location.assign(String(result.destination));
				return;
			}
			setFocus(result.outcome === "INVALID_CREDENTIALS"
				? "secret" : "identifier");
		}
		catch
		{
			setMessage("Authentication service is unavailable.");
			setFocus("identifier");
		}
	}

	return (
		<main>
			<h1>Sign on</h1>
			<form onSubmit={ submit }>
				<label>User ID
					<input name="identifier"
						maxLength={8}
					autoFocus={ focus === "identifier" } />
				</label>
				<label>Password
					<input name="credential" type="password"
						maxLength={8}
					autoFocus={ focus === "secret" } />
				</label>
				{message && <p id="credential-feedback" role="alert">{ message }</p>}
				<button type="submit">Sign on</button>
			</form>
		</main>
	);
}
