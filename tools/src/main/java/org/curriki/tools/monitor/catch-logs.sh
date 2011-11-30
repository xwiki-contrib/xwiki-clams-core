#!/bin/sh

# trap Ctrl-C
trap "stopIt" INT

stopIt() {
        echo "Aborting process."
        //killIt
        exit 0
}


(ssh appserv@prod-web tail -f /opt/coolstack/apache2/logs/www.curriki.org_access_log > apacheLog.txt )&
PID_APACHE_LOGGER="$!"

( ssh appserv@prod-app tail -f /appserv/nohup.out > appservLog.txt ) &
PID_APPSERV_LOGGER="$!"

ssh appserv@prod-app /usr/sfw/bin/top -U appserv -s 1 -n -d 60 > tops.txt

kill $PID_APACHE_LOGGER
kill $PID_APPSERV_LOGGER

