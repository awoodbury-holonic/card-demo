import { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./carddemo.css";
import AccountListPage from "../screens/AccountListPage.jsx";
import COMEN1A from "../screens/COMEN1A.jsx";
import SignOnPage from "../screens/SignOnPage.jsx";
import COADM1A from "../screens/COADM1A.jsx";
import COUSR0A from "../screens/COUSR0A.jsx";
import COUSR1A from "../screens/COUSR1A.jsx";
import COUSR2A from "../screens/COUSR2A.jsx";
import COUSR3A from "../screens/COUSR3A.jsx";
import COTRN0A from "../screens/COTRN0A.jsx";
import COTRN1A from "../screens/COTRN1A.jsx";
import COTRN2A from "../screens/COTRN2A.jsx";
import CCRDLIA from "../screens/CCRDLIA.jsx";
import CCRDSLA from "../screens/CCRDSLA.jsx";
import CardUpdateCardUpdatePage from "../screens/CardUpdateCardUpdatePage.jsx";
import CACTVWA from "../screens/CACTVWA.jsx";
import CACTUPA from "../screens/CACTUPA.jsx";
import COBIL0A from "../screens/COBIL0A.jsx";
import CORPT0A from "../screens/CORPT0A.jsx";
import COPAU0A from "../screens/COPAU0A.jsx";
import COPAU1A from "../screens/COPAU1A.jsx";
import CTRTLIA from "../screens/CTRTLIA.jsx";
import CTRTUPA from "../screens/CTRTUPA.jsx";


const routes = new Map([
	["/accounts/list", { title: "Accounts", element: <AccountListPage />, access: "AUTHENTICATED" }],
	["/menu", { title: "Main Menu", element: <COMEN1A />, access: "AUTHENTICATED" }],
	["/sign-on", { title: "Sign on", element: <SignOnPage />, access: "PUBLIC" }],
	["/admin", { title: "Administration", element: <COADM1A />, access: "ADMINISTRATOR" }],
	["/admin/users", { title: "Users", element: <COUSR0A />, access: "ADMINISTRATOR" }],
	["/admin/users/new", { title: "Add user", element: <COUSR1A />, access: "ADMINISTRATOR" }],
	["/admin/users?operation=update", { title: "Update user", element: <COUSR2A />, access: "ADMINISTRATOR" }],
	["/admin/users?operation=delete", { title: "Delete user", element: <COUSR3A />, access: "ADMINISTRATOR" }],
	["/transactions", { title: "Transactions", element: <COTRN0A />, access: "AUTHENTICATED" }],
	["/transactions?operation=view", { title: "Transaction details", element: <COTRN1A />, access: "AUTHENTICATED" }],
	["/transactions/new", { title: "Add transaction", element: <COTRN2A />, access: "AUTHENTICATED" }],
	["/cards", { title: "Credit cards", element: <CCRDLIA />, access: "AUTHENTICATED" }],
	["/cards?operation=view", { title: "Credit card details", element: <CCRDSLA />, access: "AUTHENTICATED" }],
	["/cards?operation=update", { title: "Update credit card", element: <CardUpdateCardUpdatePage />, access: "AUTHENTICATED" }],
	["/accounts", { title: "Account details", element: <CACTVWA />, access: "AUTHENTICATED" }],
	["/accounts/update", { title: "Update account", element: <CACTUPA />, access: "AUTHENTICATED" }],
	["/payments", { title: "Pay account balance", element: <COBIL0A />, access: "AUTHENTICATED" }],
	["/reports/new", { title: "Transaction report", element: <CORPT0A />, access: "AUTHENTICATED" }],
	["/authorizations/pending", { title: "Pending authorizations", element: <COPAU0A />, access: "AUTHENTICATED" }],
	["/authorizations/pending?operation=view", { title: "Authorization details", element: <COPAU1A />, access: "AUTHENTICATED" }],
	["/admin/transaction-types", { title: "Transaction types", element: <CTRTLIA />, access: "ADMINISTRATOR" }],
	["/admin/transaction-types?operation=maintain", { title: "Maintain transaction type", element: <CTRTUPA />, access: "ADMINISTRATOR" }]

]);
if (routes.size !== 22)
	throw new Error("Duplicate governed route");

function PageUnavailable()
{
	return <main><h1>Page unavailable</h1></main>;
}

function AuthenticatedPage({ route, session, onLogout })
{
	return (
		<div className="authenticated-page">
			<header className="page-toolbar">
				<a className="main-menu-button" href="/menu">Main Menu</a>
				<h1>{ route.title }</h1>
				<div className="toolbar-actions">
					{session.role === "A"
						? <a className="admin-menu-button" href="/admin">Admin Menu</a>
						: null}
					<button className="logout-button" type="button" onClick={ onLogout }>Log out</button>
				</div>
			</header>
			{ route.element }
		</div>
	);
}

function governedRouteKey(location)
{
	if (location.pathname === "/") return "/sign-on";
	const operation = new URLSearchParams(location.search).get("operation");
	return operation == null ? location.pathname
		: location.pathname + "?operation=" + encodeURIComponent(operation);
}

export default function SignOnApp()
{
	const [session, setSession] = useState(null);
	const route = routes.get(governedRouteKey(window.location));
	const logOut = async () =>
	{
		try
		{
			await fetch("/api/authentication/logout", {
				method: "DELETE", credentials: "same-origin"
			});
		}
		finally
		{
			window.location.assign("/sign-on");
		}
	};

	useEffect(() =>
	{
		if (!route || route.access === "PUBLIC") return;
		fetch("/api/session-context", { credentials: "same-origin" })
			.then(response => response.ok ? response.json() : { authenticated: false, role: "" })
			.then(setSession)
			.catch(() => setSession({ authenticated: false, role: "" }));
	}, [route]);

	if (!route) return <PageUnavailable />;
	if (route.access === "PUBLIC")
		return "false" === "true"
			? <AuthenticatedPage route={ route } session={ Object.create(null) } />
			: route.element;
	if (session == null) return <main><h1>Loading…</h1></main>;
	if (!session.authenticated)
		return <main><h1>Sign on required</h1><a href="/sign-on">Sign on</a></main>;
	if (route.access === "ADMINISTRATOR" && session.role !== "A")
		return <main><h1>Access denied</h1></main>;
	return <AuthenticatedPage route={ route } session={ session } onLogout={ logOut } />;
}

createRoot(document.getElementById("root")).render(<SignOnApp />);
