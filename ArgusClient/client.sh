#!/bin/sh
if [ -n "${ARGUSCLIENT_EXE}" ] && [ -n "${ARGUSCLIENT_CFG}" ]; then
  until java ${EXTRA_JAVA_OPTS} -Dhttps.protocols="TLSv1" -jar ${ARGUSCLIENT_EXE} -t $1 -f ${ARGUSCLIENT_CFG}; do
    if [ -n "${ARGUSCLIENT_EMAIL}" ]; then
    	mail -s "Argus $1 client automatically restarted on `hostname`" $ARGUSCLIENT_EMAIL < $ARGUSCLIENT_CFG
    fi
    echo "Argus client exited.  Respawning." >&2;
    sleep 5;
  done
else
  echo "Please set the variables ARGUSCLIENT_EXE and ARGUSCLIENT_CFG"
fi
