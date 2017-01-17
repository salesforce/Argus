#!/bin/bash
export TSDB_VERSION="2.2.0"
echo "Sleeping for 30 seconds to give HBase time to warm up"
sleep 30

if [ ! -e /opt/opentsdb_tables_created.txt ]; then
	echo "creating tsdb tables"
	bash /opt/bin/create_tsdb_tables.sh
	echo "created tsdb tables"
fi

echo "starting opentsdb"
/opt/opentsdb/opentsdb-${TSDB_VERSION}/build/tsdb tsd --port=4242 --staticroot=/opt/opentsdb/opentsdb-${TSDB_VERSION}/build/staticroot --cachedir=/tmp --auto-metric --config=/opt/opentsdb/config/opentsdb.conf
