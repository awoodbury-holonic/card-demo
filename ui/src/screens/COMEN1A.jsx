import { useEffect, useRef } from "react";

const navigationLinks = [{"choice":"1","source_label":"Account View","source_target_program":"COACTVWC","href_jsx_attribute":"/accounts","label_jsx_text":"Account view"},{"choice":"2","source_label":"Account Update","source_target_program":"COACTUPC","href_jsx_attribute":"/accounts/update","label_jsx_text":"Account update"},{"choice":"3","source_label":"Credit Card List","source_target_program":"COCRDLIC","href_jsx_attribute":"/cards","label_jsx_text":"Credit card list"},{"choice":"4","source_label":"Credit Card View","source_target_program":"COCRDSLC","href_jsx_attribute":"/cards?operation=view","label_jsx_text":"Credit card view"},{"choice":"5","source_label":"Credit Card Update","source_target_program":"COCRDUPC","href_jsx_attribute":"/cards?operation=update","label_jsx_text":"Credit card update"},{"choice":"6","source_label":"Transaction List","source_target_program":"COTRN00C","href_jsx_attribute":"/transactions","label_jsx_text":"Transaction list"},{"choice":"7","source_label":"Transaction View","source_target_program":"COTRN01C","href_jsx_attribute":"/transactions?operation=view","label_jsx_text":"Transaction view"},{"choice":"8","source_label":"Transaction Add","source_target_program":"COTRN02C","href_jsx_attribute":"/transactions/new","label_jsx_text":"Transaction add"},{"choice":"9","source_label":"Transaction Reports","source_target_program":"CORPT00C","href_jsx_attribute":"/reports/new","label_jsx_text":"Transaction reports"},{"choice":"10","source_label":"Bill Payment","source_target_program":"COBIL00C","href_jsx_attribute":"/payments","label_jsx_text":"Bill payment"},{"choice":"11","source_label":"Pending Authorization View","source_target_program":"COPAUS0C","href_jsx_attribute":"/authorizations/pending","label_jsx_text":"Pending authorization view"}];
const navigationGroups = [{"id":"accounts","label":"Accounts","sourcePrograms":["COACTVWC","COACTUPC"],"extraLinks":[{"href_jsx_attribute":"/accounts/list","label_jsx_text":"Account list"}]},{"id":"credit-cards","label":"Credit cards","sourcePrograms":["COCRDLIC","COCRDSLC","COCRDUPC"]},{"id":"transactions","label":"Transactions","sourcePrograms":["COTRN00C","COTRN01C","COTRN02C","CORPT00C"]},{"id":"payments","label":"Payments","sourcePrograms":["COBIL00C"]},{"id":"authorizations","label":"Authorizations","sourcePrograms":["COPAUS0C"]}];
const configuredPrograms = new Set(navigationGroups.flatMap(
	(group) => group.sourcePrograms));
const ungroupedLinks = navigationLinks.filter(
	(link) => !configuredPrograms.has(link.source_target_program));
const displayedGroups = navigationGroups.map((group) => ({
	...group,
	links: [...(group.extraLinks ?? []), ...navigationLinks.filter(
		(link) => group.sourcePrograms.includes(link.source_target_program))]
})).filter((group) => group.links.length > 0);
if (ungroupedLinks.length > 0)
	displayedGroups.push({ id: "other", label: "Other", links: ungroupedLinks });

export default function COMEN1A()
{
	const backActionRef = useRef(null);

	useEffect(() =>
	{
		const onBackShortcut = (event) =>
		{
			const backAction = backActionRef.current;
			if (!backAction || event.key !== backAction.getAttribute("aria-keyshortcuts") ||
				event.altKey || event.ctrlKey || event.metaKey || event.shiftKey)
				return;
			event.preventDefault();
			backAction.click();
		};
		window.addEventListener("keydown", onBackShortcut);
		return () => window.removeEventListener("keydown", onBackShortcut);
	}, []);

	return (
		<main>
			<h1>CardDemo</h1>
			<p>Choose an account, card, transaction, report, payment or authorization function.</p>
			<nav aria-label="CardDemo functions">
				<div className="menu-groups">
					{displayedGroups.map((group) => (
						<section className="menu-group" key={ group.id }>
							<h2>{ group.label }</h2>
							<ul>
								{group.links.map((link) => (
									<li key={ link.href_jsx_attribute }>
										<a href={ link.href_jsx_attribute }>{ link.label_jsx_text }</a>
									</li>
								))}
							</ul>
						</section>
					))}
				</div>
			</nav>
			<p><a ref={ backActionRef } href="/menu" aria-keyshortcuts="F3">Back to main menu</a></p>
		</main>
	);
}
