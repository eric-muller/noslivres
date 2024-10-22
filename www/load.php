<html>
  <body>
<?php

  /* database connection information */
  $sql['user']       = "";
  $sql['password']   = "";
  $sql['db']         = "";
  $sql['server']     = "";

  /************************************************/

  /* mysql connection */
  $sql['link'] =  mysql_pconnect( $sql['server'], $sql['user'], $sql['password'], 128  ) or
		die( 'could not open connection to server' );

  mysql_select_db( $sql['db'], $sql['link'] ) or
  	die( 'could not select database '. $sql['db'] );

  mysql_query("SET NAMES 'utf8'", $sql['link']);

  mysql_query ("SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO'", $sql['link']);
  mysql_query ("SET time_zone = '+00:00'", $sql['link']);
  mysql_query ("DROP TABLE IF EXISTS livres", $sql['link']);

  mysql_query ("CREATE TABLE `livres` (
  `titre`    varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci,
  `auteur`   varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci,
  `parution` varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci,
  `maj`      varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci,
  `site`     varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci,
  `url`      varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci,
  `mots`     varchar(2048) CHARACTER SET utf8 COLLATE utf8_unicode_ci,
   KEY `titre` (`titre`))
   ENGINE=MyISAM
   DEFAULT CHARACTER SET=utf8
   DEFAULT COLLATE=utf8_unicode_ci", $sql['link']);

  echo "<p>creation: </p>";
  echo "<p>";
  echo mysql_error ($sql['link']);
  echo "</p>";

  $q ="LOAD DATA LOCAL INFILE '/home/noslivre/www/books.csv' INTO TABLE `livres` CHARACTER SET utf8 FIELDS TERMINATED BY ',' ENCLOSED BY '\"' LINES TERMINATED BY '\\n'";
  echo "<p>$q</p>";

  mysql_query ($q, $sql['link']);

  echo "<p>load:</p>";
  echo "<p>";
  echo mysql_error ($sql['link']);
  echo "</p>";

  $q = "SELECT COUNT(*) FROM `livres`";
  echo "<p>$q</p>";

  $result = mysql_query ($q, $sql['link']);
  echo "<p>";
  echo mysql_result ($result, 0);
  echo " rows</p>";


  /*
  $q = "SHOW VARIABLES;";
  echo "<p>$q</p>";

  $result = mysql_query ($q, $sql['link']);
  while ($row = mysql_fetch_array ($result)) {
    echo "<p>";
    print_r ($row);
    echo "</p>"; }
  */

?>
</body>
</html>
