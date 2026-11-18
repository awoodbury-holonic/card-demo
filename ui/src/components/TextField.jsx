import { useState } from "react";

// The common editable text-field behavior. A host field arrives padded to its
// fixed width, or absent. The editable text drops only that trailing pad, so
// the input's maxLength measures what the user typed: a keystroke at the
// caret or over a selection is never refused because invisible spaces
// already fill the field, and the caret does not land after them.
export function editableText(value)
{
	return value == null ? "" : String(value).trimEnd();
}

function editableFields(names, record)
{
	return Object.fromEntries(Object.keys(names)
		.filter(name => record != null && name in record)
		.map(name => [name, editableText(record[name])]));
}

// Form state for named text fields. Every field is always a string, so each
// input stays controlled. A loaded record is merged over the current fields
// rather than replacing them, so a field the response omits (a credential,
// for example) keeps its own value. A keystroke updates from the current
// state, never from a stale snapshot of the form.
export function useTextFields(emptyFields)
{
	const [fields, setFields] = useState(() =>
		({ ...emptyFields, ...editableFields(emptyFields, emptyFields) }));
	const load = record => setFields(current =>
		({ ...current, ...editableFields(emptyFields, record) }));
	const reset = () => setFields({ ...emptyFields });
	const bind = name => ({ name, value: fields[name],
		onChange: text => setFields(current => ({ ...current, [name]: text })) });
	return { fields, load, reset, bind };
}

export default function TextField({ label, value, onChange, ...input })
{
	return (
		<label>{ label }<input { ...input } value={ value ?? "" }
			onChange={ event => onChange(event.target.value) } /></label>
	);
}
