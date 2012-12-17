#!/bin/sh
if [ "$1" = "--help" -o "$1" = "-help" -o  "$1" = "-h" ];
    then
    cat <<EOF
  Usage: shorttail.sh fileName duration
   Scans the tail of the given file, stores its content into a temp file,
   all for a given duration. Outputs the read content at the end.
EOF
exit 1;
fi

tempfile=`mktemp`

tail -f "$1" > ${tempfile} 2>&1  &

pid=$!

sleep $2

kill $pid
cat $tempfile
rm $tempfile
