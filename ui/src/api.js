export async function post(path, body)
{
	const response = await fetch(path, {
		method: "POST",
		credentials: "same-origin",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(body)
	});
	if (!response.ok) throw new Error("Request unavailable");
	return response.json();
}
