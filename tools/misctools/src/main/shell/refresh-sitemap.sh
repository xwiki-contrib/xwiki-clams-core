#!/bin/sh

## ---- settings
APPSERVHOST=http://prod-app:8080
STEP=1000
SUFFIX="000"
SPACENAME="Util/SiteMap2"
PATHTOWGET=/usr/local/bin

## ---- prepare
cd /tmp
mkdir -p sitemaps
cd sitemaps
rm *

echo "Extracting sitemap index: $APPSERVHOST/xwiki/bin/view/${SPACENAME}?xpage=plain&index=true&step=$STEP"
$PATHTOWGET/wget --quiet -O sitemap.xml "$APPSERVHOST/xwiki/bin/view/${SPACENAME}?xpage=plain&index=true&step=$STEP"

chmod ugo+r sitemap.xml

nums=`grep '<loc' sitemap.xml | sed 's|.*>.*/sitemaps/sitemap\([^<]*\).xml</.*|\1|'`
echo nums is $nums

for num in $nums
  do
    echo "Processing $num"
    min="${num}${SUFFIX}"
    echo "min is $min".
    echo Requesting "$APPSERVHOST/xwiki/bin/view/${SPACENAME}?xpage=plain&min=$min&step=$STEP"
    $PATHTOWGET/wget -O sitemap${num}.xml "$APPSERVHOST/xwiki/bin/view/${SPACENAME}?xpage=plain&min=$min&step=$STEP"
    ##    gzip sitemap${num}.xml
  done

echo "Sitemaps are ready to show on `pwd`."
## mv * /opt/coolstack/apache2/htdocs/static/
