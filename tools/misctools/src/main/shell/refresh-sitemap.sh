#!/bin/sh

## ---- settings
APPSERVHOST=http://prod-app:8080
STEP=1000
SUFFIX="000"
SPACENAME="Admin/SiteMap"
PATH=$PATH:/opt/local/bin:/usr/sfw/bin
SERVERPUBLICURL=http://beta.curriki.org

## ---- prepare
cd /tmp
mkdir -p sitemaps
cd sitemaps
rm -f *

echo "Extracting sitemap index: $APPSERVHOST/xwiki/bin/view/${SPACENAME}?xpage=plain&index=true&step=$STEP"
wget --quiet -O sitemap_tmp.xml "$APPSERVHOST/xwiki/bin/view/${SPACENAME}?xpage=plain&index=true&step=$STEP"
cat sitemap_tmp.xml | sed 's|\(<\?xml .*\?>\)|\1<?xml-stylesheet type="text/xsl" href="/sitemaps/sitemap2html.xsl"?>|' | \
   sed 's|</sitemapindex>|<sitemap><loc>'$SERVERPUBLICURL'/welcome/sitemap.xml</loc></sitemap></sitemapindex>|' \
     > sitemap.xml
rm sitemap_tmp.xml

nums=`grep '<loc' sitemap.xml | sed 's|.*>.*/sitemaps/sitemap\([^<]*\).xml</.*|\1|'`
echo nums is $nums

for num in $nums
  do
    echo "Processing $num"
    min="${num}${SUFFIX}"
    echo "min is $min".
    echo Requesting "$APPSERVHOST/xwiki/bin/view/${SPACENAME}?xpage=plain&min=$min&step=$STEP"
    wget -O sitemap${num}_tmp.xml "$APPSERVHOST/xwiki/bin/view/${SPACENAME}?xpage=plain&min=$min&step=$STEP"
    sed 's|\(<\?xml .*\?>\)|\1<?xml-stylesheet type="text/xsl" href="sitemap2html.xsl"?>|' < sitemap${num}_tmp.xml > sitemap${num}.xml
    rm sitemap${num}_tmp.xml
    ##    gzip sitemap${num}.xml
  done
cat > sitemap2html.xsl <<the_end
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0"
                xmlns:html="http://www.w3.org/TR/REC-html40"
				xmlns:image="http://www.google.com/schemas/sitemap-image/1.1"
                xmlns:sitemap="http://www.sitemaps.org/schemas/sitemap/0.9"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="html" version="1.0" encoding="UTF-8" indent="yes"/>

  <!-- ======================== SITEMAP INDEX ============================ -->
	<xsl:template match="/sitemap:sitemapindex">
		<html xmlns="http://www.w3.org/1999/xhtml">
			<head>
				<title>XML Sitemap Index</title>
				<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
			</head>
			<body>
        <h1>XML Sitemap Index</h1>
        <ul>
          <xsl:for-each select="sitemap:sitemap">
          <li><xsl:element name="a">
            <xsl:attribute name="href"><xsl:value-of select="sitemap:loc/text()"/></xsl:attribute>
            <xsl:value-of select="sitemap:loc/text()"/>
          </xsl:element></li>
          </xsl:for-each>
        </ul>
        <hr/>
        <p align="right">sitemap rendering of Curriki</p>
      </body>
    </html>
  </xsl:template>

  <!-- ======================== SITEMAP ============================ -->
	<xsl:template match="/sitemap:urlset">
		<html xmlns="http://www.w3.org/1999/xhtml">
			<head>
				<title>XML Sitemap</title>
				<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
			</head>
			<body>
        <h1>XML Sitemap</h1>
        <ul>
          <xsl:for-each select="sitemap:url">
          <li><xsl:element name="a">
            <xsl:attribute name="href"><xsl:value-of select="sitemap:loc/text()"/></xsl:attribute>
            <xsl:value-of select="sitemap:loc/text()"/>
          </xsl:element>
          <xsl:text> (</xsl:text>
          <xsl:if test="sitemap:lastmod"><xsl:value-of select="sitemap:lastmod/text()"/></xsl:if>
          <xsl:if test="sitemap:priority">, <xsl:value-of select="sitemap:priority/text()"/></xsl:if>
          <xsl:if test="sitemap:changefreq">, <xsl:value-of select="sitemap:changefreq/text()"/></xsl:if>
          <xsl:text>)</xsl:text>
          </li>
          </xsl:for-each>
        </ul>
        <hr/>
        <p align="right">sitemap rendering of Curriki</p>
      </body>
    </html>
  </xsl:template>

</xsl:stylesheet>
the_end


echo "Sitemaps are ready to show on `pwd`."
rsync -avc --delete . /opt/coolstack/apache2/htdocs/sitemaps/
