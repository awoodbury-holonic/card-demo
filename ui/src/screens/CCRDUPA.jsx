export default function CCRDUPA()
{
	return (
		<section data-screen="CCRDUPA">
			<input name="TRNNAME" aria-label="TRNNAME" style={{ gridRow: 1, gridColumn: 7 }} defaultValue="" readOnly={true} />
			<input name="TITLE01" aria-label="TITLE01" style={{ gridRow: 1, gridColumn: 21 }} defaultValue="" readOnly={true} />
			<input name="CURDATE" aria-label="CURDATE" style={{ gridRow: 1, gridColumn: 71 }} defaultValue="mm/dd/yy" readOnly={true} />
			<input name="PGMNAME" aria-label="PGMNAME" style={{ gridRow: 2, gridColumn: 7 }} defaultValue="" readOnly={true} />
			<input name="TITLE02" aria-label="TITLE02" style={{ gridRow: 2, gridColumn: 21 }} defaultValue="" readOnly={true} />
			<input name="CURTIME" aria-label="CURTIME" style={{ gridRow: 2, gridColumn: 71 }} defaultValue="hh:mm:ss" readOnly={true} />
			<input name="ACCTSID" aria-label="ACCTSID" style={{ gridRow: 7, gridColumn: 45 }} defaultValue="" readOnly={true} />
			<input name="CARDSID" aria-label="CARDSID" style={{ gridRow: 8, gridColumn: 45 }} defaultValue="" readOnly={false} />
			<input name="CRDNAME" aria-label="CRDNAME" style={{ gridRow: 11, gridColumn: 25 }} defaultValue="" readOnly={false} />
			<input name="CRDSTCD" aria-label="CRDSTCD" style={{ gridRow: 13, gridColumn: 25 }} defaultValue="" readOnly={false} />
			<input name="EXPMON" aria-label="EXPMON" style={{ gridRow: 15, gridColumn: 25 }} defaultValue="" readOnly={false} />
			<input name="EXPYEAR" aria-label="EXPYEAR" style={{ gridRow: 15, gridColumn: 30 }} defaultValue="" readOnly={false} />
			<input name="EXPDAY" aria-label="EXPDAY" style={{ gridRow: 15, gridColumn: 36 }} defaultValue="" readOnly={true} />
			<input name="INFOMSG" aria-label="INFOMSG" style={{ gridRow: 20, gridColumn: 25 }} defaultValue="" readOnly={true} />
			<input name="ERRMSG" aria-label="ERRMSG" style={{ gridRow: 23, gridColumn: 1 }} defaultValue="" readOnly={true} />
			<input name="FKEYS" aria-label="FKEYS" style={{ gridRow: 24, gridColumn: 1 }} defaultValue="ENTER=Process F3=Exit" readOnly={true} />
			<input name="FKEYSC" aria-label="FKEYSC" style={{ gridRow: 24, gridColumn: 23 }} defaultValue="F5=Save F12=Cancel" readOnly={true} />
		</section>
	);
}
