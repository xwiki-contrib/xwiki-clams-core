#!/bin/bash
##  ---- recreate-lang-files.sh
##   This script is copied and adapted from
##    Intergeo's Platform/comped/src/main/resources/recreate.sh
##   (which is doing properties) by Henri Lesourd.
##
##   It is meant to be run in a directory where the lang_xx.js files are output
##   as fetched by the server's Util/JSTrans.
##
##   It is meant to work with mod_rewrite's
##    
##
DIR=`which $0`
cd `dirname $DIR`
. ./settings.sh
##
## langs=`./fetchlanguages.sh`
langs=`cat languages.txt`
TMP=`mktemp -d ./TMPXXXXXX`
cd $TMP
for l in $langs ; do
  URL="$baseURL/xwiki/bin/view/Util/JSTrans?xpage=plain&language=${l}"
  echo "fetching $URL"
  TMP2=`mktemp ./TMPXXX`
  NAME="lang_${l}.js"
  curl -s "$URL" > $TMP2
  mv $TMP2 $NAME
  echo "Done with ${NAME}."
done

rsync -avc ./ ../
cd ..
chmod ugo+r *.js
rm -r $TMP
