export default function TransactionReportRequestPage()
{
	return (<main data-online-unit="TransactionReportRequestPage">
{/* transaction entry and action dispatch */}

{/* |- default-render: online_condition_state_field (no style rule) */}
{/* update cdemoPgmContext from the source expression */}
{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
<TerminalScreen direction="receive" target="CORPT0A" />
{/* |- default-render: logged_field_error (no style rule) */}
<output>{wsReasCd}</output>
{/* update wsErrFlg from the source expression */}
{/* update wsMessage from the source expression */}
{/* update monthlyl from the source expression */}
{/* invoke sendTrnrptScreen */}
{/* |- default-render: checked_td_queue_write_feedback (no style rule) */}
{/* write through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update monthlyl from the source expression */}
	{/* invoke sendTrnrptScreen */}

{/* |- default-render: confirmed_bounded_td_queue_submission (no style rule) */}
{/* conditional branch; nested effects retain source order */}
	{/* update wsMessage from the source expression */}
	{/* update wsErrFlg from the source expression */}
	{/* update confirml from the source expression */}
	{/* invoke sendTrnrptScreen */}

{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* invoke initializeAllFields */}
	{/* update wsErrFlg from the source expression */}
	{/* invoke sendTrnrptScreen */}
	{/* update wsMessage from the source expression */}
	{/* update wsErrFlg from the source expression */}
	{/* update confirml from the source expression */}
	{/* invoke sendTrnrptScreen */}

{/* repeat the covered batch operation until its terminal condition */}
	{/* update jclRecord from the source expression */}
	{/* conditional branch; nested effects retain source order */}
		{/* emit the transaction diagnostic */}

	{/* invoke wirteJobsubTdq */}

{/* write through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update monthlyl from the source expression */}
	{/* invoke sendTrnrptScreen */}

{/* |- default-render: report_submission_controller_eligibility (no style rule) */}
{/* invoke the selected external service */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* conditional branch; nested effects retain source order */}
		{/* update wsMessage from the source expression */}
		{/* update wsErrFlg from the source expression */}
		{/* update sdtmml from the source expression */}
		{/* invoke sendTrnrptScreen */}


{/* invoke the selected external service */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* conditional branch; nested effects retain source order */}
		{/* update wsMessage from the source expression */}
		{/* update wsErrFlg from the source expression */}
		{/* update edtmml from the source expression */}
		{/* invoke sendTrnrptScreen */}


{/* conditional branch; nested effects retain source order */}
	{/* update wsMessage from the source expression */}
	{/* update wsErrFlg from the source expression */}
	{/* update confirml from the source expression */}
	{/* invoke sendTrnrptScreen */}

{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* invoke initializeAllFields */}
	{/* update wsErrFlg from the source expression */}
	{/* invoke sendTrnrptScreen */}
	{/* update wsMessage from the source expression */}
	{/* update wsErrFlg from the source expression */}
	{/* update confirml from the source expression */}
	{/* invoke sendTrnrptScreen */}

{/* repeat the covered batch operation until its terminal condition */}
	{/* update jclRecord from the source expression */}
	{/* conditional branch; nested effects retain source order */}
		{/* emit the transaction diagnostic */}

	{/* invoke wirteJobsubTdq */}

{/* write through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update monthlyl from the source expression */}
	{/* invoke sendTrnrptScreen */}

{/* render the selected screen map */}
 {/* |- default-render: callable_validation_caller (no style rule) */}
{/* invoke the selected external service */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* conditional branch; nested effects retain source order */}
		{/* update wsMessage from the source expression */}
		{/* update wsErrFlg from the source expression */}
		{/* update sdtmml from the source expression */}
		{/* invoke sendTrnrptScreen */}


{/* |- default-render: callable_validation_caller (no style rule) */}
{/* invoke the selected external service */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* conditional branch; nested effects retain source order */}
		{/* update wsMessage from the source expression */}
		{/* update wsErrFlg from the source expression */}
		{/* update edtmml from the source expression */}
		{/* invoke sendTrnrptScreen */}


{/* |- default-render: conditional_transfer_with_setup (no style rule) */}
{/* conditional branch; nested effects retain source order */}
	{/* update cdemoToProgram from the source expression */}

{/* update cdemoFromTranid from the source expression */}
{/* update cdemoFromProgram from the source expression */}
{/* update cdemoPgmContext from the source expression */}
{/* transfer control to the selected program */}
	</main>);
}
