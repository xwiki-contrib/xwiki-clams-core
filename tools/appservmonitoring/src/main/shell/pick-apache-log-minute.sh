#!/bin/sh
filename=`ssh appserv@prod-web "ls -lt /opt/coolstack/apache2/logs/" | grep www | grep access | head -n 1 | awk '{print $9}'`
echo filename is $filename
ssh appserv@prod-web "./shorttail.sh /opt/coolstack/apache2/logs/$filename 60"
