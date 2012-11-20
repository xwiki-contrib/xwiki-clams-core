#!/bin/sh
BASEURL="http://localhost:8080"
echo
echo
echo ---- warming up host $BASEURL -----------------
echo
echo -------------------- REQUESTING HOME PAGE ----------------
echo ------------------------ `date` --------------------------
echo ----------------------------------------------------------
echo
wget --quiet --save-headers -O - $BASEURL"/xwiki/bin/view/Main/WebHomeVistor" | head | grep "^HTTP"
echo
echo
echo
echo --------------- REQUESTING ONE VELOCITY RENDERING -------------------------
echo ------------------------ `date` --------------------------
echo ---------------------------------------------------------------------------
echo 
wget --quiet --save-headers -O - $BASEURL"/xwiki/bin/view/Main/AboutConnecting" | head | grep "^HTTP"
echo 
echo
echo ---------------- REQUESTING ONE ATTACHMENT DOWNLOAD -----------------------
echo ------------------------ `date` --------------------------
echo ---------------------------------------------------------------------------
echo 
wget --quiet --save-headers -O - $BASEURL"/xwiki/bin/download/Main/WebHomeVistor/images.zip/images/video%20position%20img.png" | head | grep "^HTTP"
echo 
echo
echo ------------------- REQUESTING ONE LUCENE INDEX ACCESS --------------------
echo ------------------------ `date` --------------------------
echo ---------------------------------------------------------------------------
echo 
wget --quiet --save-headers -O - $BASEURL"/xwiki/bin/view/Search/Members?start=0&limit=25&xpage=plain&_dc=1293583818710&module=member&terms=&other=&subjectparent=&language=&review=&ictprfx=activity&ict=activity_exercise&special=&sort=title&dir=ASC"  | head | grep "^HTTP"
echo
echo
echo ------------------- REQUESTING ONE DOCUMENT SAVE --------------------
echo ------------------------ `date` --------------------------
echo ---------------------------------------------------------------------------
echo
curlOpts="  "
UPLOAD_TARGET=`cat upload_target` ## includes user-name and password
export UPLOAD_TARGET
spacename="Coll_`echo $UPLOAD_TARGET | sed 's|http://||' | sed 's|:.*||'`"
pagename="Try"
pageBody="This is a test"
echo $pageBody | curl $curlOpts -k -F comment="cli-upload." -F content="<-" "$UPLOAD_TARGET/xwiki/bin/save/$spacename/$pagename?language=$language"
## wget --quiet --save-headers -O - $BASEURL"/xwiki/bin/view/$spacename/$pagename?xpage=plain"
echo
echo --------------------------- CHECKING SAVED DOCUMENT ------------------------------
echo
curl $curlOpts "$UPLOAD_TARGET/xwiki/bin/view/$spacename/$pagename?language=$language&xpage=plain"
received=`curl "$UPLOAD_TARGET/xwiki/bin/view/$spacename/$pagename?language=$language&xpage=plain"`
if [ "$received" = "$pageBody" ]; then
  echo "Ok, has correctly saved."
else
  echo "WARNING: save was not successful"
  echo " ------------------------------- "
  echo " received: "
  echo " ------------------------------- "
  echo $received
fi
echo
echo
echo --------------------------- FINISHED WARM-UP ------------------------------
echo ------------------------ `date` --------------------------
echo ---------------------------------------------------------------------------

