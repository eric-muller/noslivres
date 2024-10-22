if [ -f www/books.csv ]; then
  mv www/books.csv www/previous.csv
fi

CLASSPATH='code/src:code/commons-compress-1.8.1.jar:code/commons-io-2.4.jar' \
	 java net.noslivres.catalog.Catalog 

