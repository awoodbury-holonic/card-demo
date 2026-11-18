export default function AuthorizationDetailPage()
{
	return (<main data-online-unit="AuthorizationDetailPage">
{/* |- default-render: ims_navigation_operation (no style rule) */}
{/* |- default-render: marker (no style rule) at source line 465 offset 43406; source-statement: <!#step { .body = "", .cfg_pred = "43382", .cfg_succ = "43586", .command = "GNP", .cpt = "exec_dli_stmt", .interface = "GNP", .kind = "marker", .line = 465, .medium = "ims", .offset = 43406, .reason = "EXEC DLI syntax admitted; program-to-PSB binding remains deployment policy", .walk_id = 0 }!> */}
{/* |- default-render: ims_navigation_operation (no style rule) */}
{/* |- default-render: marker (no style rule) at source line 495 offset 44228; source-statement: <!#step { .body = "", .cfg_succ = "44345", .command = "GNP", .cpt = "exec_dli_stmt", .interface = "GNP", .invoked_from = "37852", .kind = "marker", .line = 495, .medium = "ims", .offset = 44228, .reason = "EXEC DLI syntax admitted; program-to-PSB binding remains deployment policy", .walk_id = 0 }!> */}
{/* |- default-render: ims_keyed_detail_inquiry (no style rule) */}
{/* |- default-render: marker (no style rule) at source line 439 offset 42675; source-statement: <!#step { .body = "", .cfg_pred = "42616", .cfg_succ = "42828", .command = "GU", .cpt = "exec_dli_stmt", .interface = "GU", .kind = "marker", .line = 439, .medium = "ims", .offset = 42675, .reason = "EXEC DLI syntax admitted; program-to-PSB binding remains deployment policy", .walk_id = 0 }!> */}
{/* update imsReturnCode from the source expression */}
{/* conditional branch; nested effects retain source order */}
	{/* emit the transaction diagnostic */}
	{/* emit the transaction diagnostic */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* invoke sendAuthviewScreen */}

{/* |- default-render: marker (no style rule) at source line 465 offset 43406; source-statement: <!#step { .body = "", .cfg_pred = "43382", .cfg_succ = "43586", .command = "GNP", .cpt = "exec_dli_stmt", .interface = "GNP", .kind = "marker", .line = 465, .medium = "ims", .offset = 43406, .reason = "EXEC DLI syntax admitted; program-to-PSB binding remains deployment policy", .walk_id = 8 }!> */}
{/* update imsReturnCode from the source expression */}
{/* conditional branch; nested effects retain source order */}
	{/* emit the transaction diagnostic */}
	{/* emit the transaction diagnostic */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* invoke sendAuthviewScreen */}

{/* |- default-render: ims_guarded_detail_projection (no style rule) */}
{/* conditional branch; nested effects retain source order */}
	{/* update authfrdo from the source expression */}
	{/* update authfrdo from the source expression */}
	{/* update authfrdo from the source expression */}
	{/* update authfrdo from the source expression */}

{/* transaction entry and action dispatch */}

{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
<TerminalScreen direction="receive" target="COPAU1A" />
 {/* |- default-render: browse_dispatch_direction (no style rule) */}
{/* conditional branch; nested effects retain source order */}
	{/* invoke takeSyncpoint */}
	{/* conditional branch; nested effects retain source order */}
		{/* update wsMessage from the source expression */}
		{/* update wsMessage from the source expression */}

	{/* invoke rollBack */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* invoke sendAuthviewScreen */}

{/* |- default-render: ims_psb_schedule (no style rule) */}
{/* |- default-render: marker (no style rule) at source line 575 offset 46551; source-statement: <!#step { .body = "", .cfg_succ = "46626", .command = "SCHD", .cpt = "exec_dli_stmt", .interface = "SCHD", .invoked_from = "42542", .kind = "marker", .line = 575, .medium = "ims", .offset = 46551, .reason = "EXEC DLI syntax admitted; program-to-PSB binding remains deployment policy", .walk_id = 0 }!> */}
{/* update imsReturnCode from the source expression */}
{/* conditional branch; nested effects retain source order */}
	{/* |- default-render: marker (no style rule) at source line 581 offset 46708; source-statement: <!#step { .body = "", .cfg_pred = "46669", .cfg_succ = "46745", .command = "TERM", .cpt = "exec_dli_stmt", .interface = "TERM", .kind = "marker", .line = 581, .medium = "ims", .offset = 46708, .parent_source_offset = 46669, .reason = "EXEC DLI syntax admitted; program-to-PSB binding remains deployment policy", .walk_id = 3, .walk_parent_id = 2 }!> */}
	{/* |- default-render: marker (no style rule) at source line 584 offset 46745; source-statement: <!#step { .body = "", .cfg_pred = "46708", .cfg_succ = "46832", .command = "SCHD", .cpt = "exec_dli_stmt", .interface = "SCHD", .kind = "marker", .line = 584, .medium = "ims", .offset = 46745, .parent_source_offset = 46669, .reason = "EXEC DLI syntax admitted; program-to-PSB binding remains deployment policy", .walk_id = 4, .walk_parent_id = 2 }!> */}
	{/* update imsReturnCode from the source expression */}

{/* conditional branch; nested effects retain source order */}
	{/* emit the transaction diagnostic */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* invoke sendAuthviewScreen */}

	</main>);
}
