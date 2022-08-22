<?php

  /* database connection information */
  $sql['user']       = "";
  $sql['password']   = "";
  $sql['db']         = "";
  $sql['server']     = "";
	

/*
_	1376471719853
bRegex	false
bRegex_0	false
bRegex_1	false
bRegex_2	false
bSearchable_0	true
bSearchable_1	true
bSearchable_2	true
bSortable_0	true
bSortable_1	true
bSortable_2	true
iColumns	3
iDisplayLength	10
iDisplayStart	0
iSortCol_0	0
iSortingCols	1
mDataProp_0	0
mDataProp_1	1
mDataProp_2	2
sColumns	
sEcho	11
sSearch	maup toin
sSearch_0	
sSearch_1	
sSearch_2	
sSortDir_0	asc
*/


  /* array of database columns which should be read and sent back
     to datatables. use a space where you want to insert a non-database 
     field (for example a counter or static image) */
  $acolumns = array( 'titre', 'auteur', 'parution', 'maj', 'site', 'url');
	
  /* indexed column (used for fast and accurate table cardinality) */
  $sIndexColumn = "titre";
	
  /* db table to use */
  $sTable = "livres";
	
  /************************************************/

  /* mysql connection */
  $sql['link'] =  mysql_pconnect( $sql['server'], $sql['user'], $sql['password']  ) or
		die( 'could not open connection to server' );
	
  mysql_select_db( $sql['db'], $sql['link'] ) or 
  	die( 'could not select database '. $sql['db'] );
	
  mysql_query("SET NAMES 'utf8'");        	

  /* 
   * paging
   */
  $sLimit = "";
    if ( isset( $_GET['start'] ) && $_GET['length'] != '-1' ) {
      $sLimit = "LIMIT ".intval( $_GET['start'] )
                        .", "
      			.intval( $_GET['length'] ); }
	
	

       if ( isset($request['order']) && count($request['order']) ) {
            $orderBy = array();
            $dtColumns = SSP::pluck( $columns, 'dt' );

            for ( $i=0, $ien=count($request['order']) ; $i<$ien ; $i++ ) {
                // Convert the column index into the column data property
                $columnIdx = intval($request['order'][$i]['column']);
                $requestColumn = $request['columns'][$columnIdx];

                $columnIdx = array_search( $requestColumn['data'], $dtColumns );
                $column = $columns[ $columnIdx ];

                if ( $requestColumn['orderable'] == 'true' ) {
                    $dir = $request['order'][$i]['dir'] === 'asc' ?
                        'ASC' :
                        'DESC';

                    $orderBy[] = '`'.$column['db'].'` '.$dir;
                }
            }

            $order = 'ORDER BY '.implode(', ', $orderBy);
        }

 
  /*
   * ordering
   */
  $sOrder = "";
  if ( isset( $_GET['order'] )) {
    $sOrder = "ORDER BY  ";

    for ( $i=0 ; $i<count( $_GET['order'] ) ; $i++ ) {
      	$sOrder .= $acolumns [intval ($_GET ['order'][$i]['column'])]
                   ." "
                   .($_GET['order'][$i]['dir']==='asc' ? 'ASC' : 'DESC') 
                   .", "; }
		
    $sOrder = substr_replace( $sOrder, "", -2 );
    if ( $sOrder == "ORDER BY" ) {
      $sOrder = "";	}}
	
        /*  echo json_encode ($sOrder);*/
	
  /* 
   * filtering
   */
  $sWhere = "";
  if ( isset($_GET['search']) && $_GET['search']['value'] != "" ) {
    $sWhere = "WHERE (";
    $words = explode (" ", $_GET['search']['value']);
    for ( $i=0; $i<count($words); $i++) {
      $sWhere .= "mots LIKE '%"
                 .mysql_real_escape_string ($words[$i])
                 ."%' AND "; }
    $sWhere = substr_replace( $sWhere, "", -4 );
    $sWhere .= ')'; }
	
  /*
   * sql queries
   * get data to display
   */
  $sQuery = "SELECT SQL_CALC_FOUND_ROWS ".str_replace(" , ", " ", implode(", ", $acolumns))." FROM   $sTable $sWhere $sOrder $sLimit";
  $sQuery2 = $sQuery;
  $rResult = mysql_query( $sQuery, $sql['link'] ) or die(mysql_error());
	
  /* Data set length after filtering */
  $sQuery = "SELECT FOUND_ROWS()";
  $rResultFilterTotal = mysql_query( $sQuery, $sql['link'] ) or die(mysql_error());
  $aResultFilterTotal = mysql_fetch_array($rResultFilterTotal);
  $iFilteredTotal = $aResultFilterTotal[0];
	
  /* Total data set length */
  $sQuery = " SELECT COUNT(".$sIndexColumn.") FROM $sTable";
  $rResultTotal = mysql_query( $sQuery, $sql['link'] ) or die(mysql_error());
  $aResultTotal = mysql_fetch_array($rResultTotal);
  $iTotal = $aResultTotal[0];
	
  /*
   * Output
   */
  $output = array(
                "query" => $sQuery2,
		"draw" => intval($_GET['draw']),
		"recordsTotal" => $iTotal,
		"recordsFiltered" => $iFilteredTotal,
		"data" => array()
	);
	
  while ( $aRow = mysql_fetch_array( $rResult ) ) {
    $row = array();
    $row[] = $aRow ["titre"];
    $row[] = $aRow ["auteur"];
    $row[] = $aRow ["parution"];
    $row[] = $aRow ["maj"];
    $row[] = "<a href='".$aRow ["url"]."'>".$aRow ["site"]."</a>";
    $output['data'][] = $row;	}

  echo json_encode( $output );
?>

