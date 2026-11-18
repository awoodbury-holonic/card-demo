export default function COADM1A()
{
	return (
		<main>
			<h1>Administration</h1>
			<p>Manage users and transaction types.</p>
			<nav aria-label="Administration functions">
				<ul>
					<li><a href="/admin/users">List users</a></li>
					<li><a href="/admin/users/new">Add user</a></li>
					<li><a href="/admin/users?operation=update">Update user</a></li>
					<li><a href="/admin/users?operation=delete">Delete user</a></li>
					<li><a href="/admin/transaction-types">List transaction types</a></li>
					<li><a href="/admin/transaction-types?operation=maintain">Maintain transaction type</a></li>
				</ul>
			</nav>
		</main>
	);
}
