#!/bin/sh
if [ -n "${ARGUSCLIENT_EXE}" ] && [ -n "${ARGUSCLIENT_CFG}" ] && [ -n "${ARGUSCLIENT_EMAIL}" ]; then
  until java -Dhttps.protocols="TLSv1" -jar ${ARGUSCLIENT_EXE} -t $1 -f ${ARGUSCLIENT_CFG}; do
    mail -s "Argus $1 client automatically restarted on `hostname`" $ARGUSCLIENT_EMAIL < $ARGUSCLIENT_CFG
    echo "Argus client exited.  Respawning." >&2;
    sleep 5;
  done
else
  echo "Please set the variables ARGUSCLIENT_EXE, ARGUSCLIENT_CFG and ARGUSCLIENT_EMAIL"
fi
