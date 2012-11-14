#!/bin/sh

## This script takes one parameter: the file-name
## and one environment variable: AUTHHOST in the form of http://host/xwiki
## It saves the document name at the page with its same name (less the extensions)
## within a space of the directory it contains.

UPLOAD_HOST=`echo $UPLOAD_TO | sed 's|.*://\([^/]*\).*|\1|'`
UPLOAD_AUTH=`grep $UPLOAD_HOST ${HOME}/.upload-auth | sed 's|[^ ]*  *||'`
export UPLOAD_AUTH
UPLOAD_TARGET=`echo $UPLOAD_TO | sed "s|://|://${UPLOAD_AUTH}@|"`

for file in "$@" ; do
pagename=`basename "$file" | sed 's/\(.*\)\.[a-z]*/\1/'`
language=`echo "$pagename"| sed 's/.*\.\([a-z][a-z]\)$/\1/'`
pagename=`echo "$pagename" | sed 's/\(.*\)\.[a-z]*/\1/'`
spacename=`dirname "$file"`
spacename=`cd $spacename && pwd`
spacename=`basename $spacename`


if [ "$pagename" = "$language" ];
then
  language=en
fi

## echo auth $UPLOAD_AUTH
if [ "x$UPLOAD_AUTH" = "x" ];
then
  echo ""
  echo "ERROR: Missing auth."
  echo ""
  exit 1
fi

## echo UPLOAD_TARGET is $UPLOAD_TARGET
echo Uploading to $spacename/$pagename, language $language to $UPLOAD_HOST...
cat "$file" |
  curl -k -F comment="cli-upload." -F content="<-" "$UPLOAD_TARGET/xwiki/bin/save/$spacename/$pagename?language=$language"
  statusNumber="$?"
  if [ $statusNumber != 0 ];
  then
  	  echo "Failed: error code ${statusNumber}."
  fi
done
