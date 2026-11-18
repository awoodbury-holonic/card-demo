import { useEffect, useRef, useState } from "react";

const emptyPage = () => ({ data: null, rows: [], history: [], index: 0, hasPrevious: false, hasNext: false });

function nextRequest(dataset, data, request)
{
	if (dataset.hasNextProperty && !data[dataset.hasNextProperty]) return null;
	if (dataset.isLastProperty && data[dataset.isLastProperty]) return null;
	if (dataset.pageParameter)
		return { ...request, [dataset.pageParameter]: Number(request[dataset.pageParameter]) + 1 };
	const rows = data[dataset.rowsProperty];
	const cursor = dataset.nextKeyProperty ? data[dataset.nextKeyProperty] : rows.at(-1)?.[dataset.rowKeyProperty];
	if (cursor == null || cursor === "") return null;
	return { ...request, ...dataset.nextParameters, [dataset.cursorParameter]: String(cursor) };
}

export function usePagedRecords(dataset, initialRequest)
{
	const [page, setPage] = useState(emptyPage);
	const [pending, setPending] = useState(() => initialRequest == null ? null
		: { request: initialRequest, index: 0, reset: true, generation: 0 });
	const [loading, setLoading] = useState(initialRequest != null);
	const [error, setError] = useState("");
	const generation = useRef(0);
	const busy = useRef(initialRequest != null);

	useEffect(() =>
	{
		if (!pending) return;
		const controller = new AbortController();
		const current = () => !controller.signal.aborted && pending.generation === generation.current;
		fetch(dataset.endpoint + "?" + new URLSearchParams(pending.request),
			{ credentials: "same-origin", signal: controller.signal })
			.then(async (response) =>
			{
				if (!response.ok) throw new Error(dataset.failureMessage || "The list could not be loaded.");
				const data = await response.json();
				if (!current()) return;
				if (dataset.errorProperty && data[dataset.errorProperty])
					throw new Error(data[dataset.messageProperty] || dataset.failureMessage);
				const rows = data?.[dataset.rowsProperty];
				if (!Array.isArray(rows)) throw new Error("The list response is invalid.");
				if (!pending.reset && rows.length === 0)
				{
					setPage(previous => ({ ...previous,
						hasPrevious: pending.index < previous.index ? false : previous.hasPrevious,
						hasNext: pending.index > previous.index ? false : previous.hasNext }));
					return;
				}
				setPage(previous => ({ data, rows, index: pending.index,
					history: [...(pending.reset ? [] : previous.history.slice(0, pending.index)), pending.request],
					hasPrevious: pending.index > 0,
					hasNext: rows.length > 0 && nextRequest(dataset, data, pending.request) != null }));
			})
			.catch((failure) =>
			{
				if (!current()) return;
				if (pending.reset) setPage(emptyPage());
				setError(failure.message);
			})
			.finally(() =>
			{
				if (!current()) return;
				busy.current = false;
				setLoading(false);
			});
		return () => controller.abort();
	}, [dataset, pending]);

	function requestPage(request, index, reset)
	{
		busy.current = true;
		setLoading(true);
		setError("");
		setPending({ request, index, reset, generation: ++generation.current });
	}

	function previous()
	{
		if (busy.current || !page.hasPrevious) return;
		requestPage(page.history[page.index - 1], page.index - 1, false);
	}

	function next()
	{
		if (busy.current || !page.hasNext) return;
		const request = nextRequest(dataset, page.data, page.history[page.index]);
		if (request) requestPage(request, page.index + 1, false);
	}

	return { data: page.data, rows: page.rows, loading, error, setError,
		hasPrevious: page.hasPrevious, hasNext: page.hasNext,
		search: request => requestPage(request, 0, true), previous, next };
}

export default function PageNavigation({ page, children })
{
	return (
		<nav className="table-pagination" aria-label="List pages">
			<button type="button" disabled={page.loading || !page.hasPrevious} onClick={ page.previous }>Previous page</button>
			<button type="button" disabled={page.loading || !page.hasNext} onClick={ page.next }>Next page</button>
			{ children }
		</nav>
	);
}
