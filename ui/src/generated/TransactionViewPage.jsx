export default function TransactionViewPage()
{
	return (<main data-online-unit="TransactionViewPage">
{/* transaction entry and action dispatch */}

{/* |- default-render: online_condition_state_field (no style rule) */}
{/* update cdemoPgmContext from the source expression */}
{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
{/* |- default-render: online_binary_condition_state (no style rule) */}
{/* update wsErrFlg from the source expression */}
{/* refresh the screen with the pending feedback message */}
{/* invoke populateHeaderInfo */}
{/* update errmsgo from the source expression */}
{/* render the selected screen map */}

<TerminalScreen direction="receive" target="COTRN1A" />
{/* |- default-render: logged_field_error (no style rule) */}
<output>{wsReasCd}</output>
{/* update wsErrFlg from the source expression */}
{/* update wsMessage from the source expression */}
{/* update trnidinl from the source expression */}
{/* invoke sendTrnviewScreen */}
  {/* |- default-render: form_reset_intent (no style rule) */}
{/* update trnidinl from the source expression */}
{/* update wsMessage from the source expression */}
{/* |- default-render: keyed_detail_lookup_outcome (no style rule) */}
{/* read through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}

{/* |- default-render: keyed_detail_checked_read_outcome (no style rule) */}
{/* read through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}

{/* |- default-render: checked_read_value_snapshot (no style rule) */}
{/* invoke readTransactFile */}
{/* read through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}

{/* |- default-render: detail_record_projection (no style rule) */}
{/* invoke readTransactFile */}
{/* update wsTranAmt from the source expression */}
{/* update trnidi from the source expression */}
{/* update cardnumi from the source expression */}
{/* update ttypcdi from the source expression */}
{/* update tcatcdi from the source expression */}
{/* update trnsrci from the source expression */}
{/* update trnamti from the source expression */}
{/* update tdesci from the source expression */}
{/* update torigdti from the source expression */}
{/* update tprocdti from the source expression */}
{/* update midi from the source expression */}
{/* update mnamei from the source expression */}
{/* update mcityi from the source expression */}
{/* update mzipi from the source expression */}
{/* read through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}

{/* |- default-render: detail_result_value_adapter (no style rule) */}
{/* invoke readTransactFile */}
{/* update wsTranAmt from the source expression */}
{/* update trnidi from the source expression */}
{/* update cardnumi from the source expression */}
{/* update ttypcdi from the source expression */}
{/* update tcatcdi from the source expression */}
{/* update trnsrci from the source expression */}
{/* update trnamti from the source expression */}
{/* update tdesci from the source expression */}
{/* update torigdti from the source expression */}
{/* update tprocdti from the source expression */}
{/* update midi from the source expression */}
{/* update mnamei from the source expression */}
{/* update mcityi from the source expression */}
{/* update mzipi from the source expression */}
{/* read through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}

{/* |- default-render: keyed_detail_presented_request_intent (no style rule) */}
{/* conditional branch; nested effects retain source order */}
	{/* update cdemoToProgram from the source expression */}
	{/* invoke returnToPrevScreen */}
	{/* update carddemoCommarea from the source expression */}
	{/* conditional branch; nested effects retain source order */}
		{/* emit the transaction diagnostic */}
		{/* update cotrn1ao from the source expression */}
		{/* update trnidinl from the source expression */}
		{/* conditional branch; nested effects retain source order */}
			{/* update trnidini from the source expression */}
			{/* invoke processEnterKey */}

		{/* invoke sendTrnviewScreen */}
		{/* invoke receiveTrnviewScreen */}
		{/* conditional branch; nested effects retain source order */}
			{/* invoke processEnterKey */}
			{/* conditional branch; nested effects retain source order */}
				{/* update cdemoToProgram from the source expression */}
				{/* update cdemoToProgram from the source expression */}

			{/* invoke returnToPrevScreen */}
			{/* invoke clearCurrentScreen */}
			{/* update cdemoToProgram from the source expression */}
			{/* invoke returnToPrevScreen */}
			{/* update wsErrFlg from the source expression */}
			{/* update wsMessage from the source expression */}
			{/* invoke sendTrnviewScreen */}



{/* return control to the transaction environment */}
{/* invoke readTransactFile */}
{/* update wsTranAmt from the source expression */}
{/* update trnidi from the source expression */}
{/* update cardnumi from the source expression */}
{/* update ttypcdi from the source expression */}
{/* update tcatcdi from the source expression */}
{/* update trnsrci from the source expression */}
{/* update trnamti from the source expression */}
{/* update tdesci from the source expression */}
{/* update torigdti from the source expression */}
{/* update tprocdti from the source expression */}
{/* update midi from the source expression */}
{/* update mnamei from the source expression */}
{/* update mcityi from the source expression */}
{/* update mzipi from the source expression */}
{/* render the selected screen map */}
{/* render the selected screen map */}
{/* read through the resolved resource path */}
{/* conditional branch; nested effects retain source order */}
	{/* no operation */}
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}
	<output>{wsReasCd}</output>
	{/* update wsErrFlg from the source expression */}
	{/* update wsMessage from the source expression */}
	{/* update trnidinl from the source expression */}
	{/* invoke sendTrnviewScreen */}

{/* invoke initializeAllFields */}
{/* invoke sendTrnviewScreen */}
{/* update trnidinl from the source expression */}
{/* update wsMessage from the source expression */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_field (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: record_io_model_decimal_contract (no style rule) */}
{/* read through the resolved resource path */}
{/* |- default-render: conditional_transfer_with_setup (no style rule) */}
{/* conditional branch; nested effects retain source order */}
	{/* update cdemoToProgram from the source expression */}

{/* update cdemoFromTranid from the source expression */}
{/* update cdemoFromProgram from the source expression */}
{/* update cdemoPgmContext from the source expression */}
{/* transfer control to the selected program */}
	</main>);
}
