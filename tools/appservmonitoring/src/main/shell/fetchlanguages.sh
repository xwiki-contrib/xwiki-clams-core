#!/bin/bash
##
## copied from Intergeo's
##  Platform/comped/src/main/resources/fetchlanguages.sh
## by Henri Lesourd.
##  bases on the script XWiki.SupportedLanguages with the source below
##
DIR=`which $0`
cd `dirname $DIR`
. ./settings.sh
curl -s "${baseURL}/xwiki/bin/view/XWiki/SupportedLanguages?xpage=plain&len=$1"
exit 0
##
##

#set($len=$request.getParameter("len"))
#if (!$len || $len!="long")
#set($len="short")
#end
#if ($len=="short")
$xwiki.getXWikiPreference("languages").replaceAll(","," ")
#else
#set($lib=$xwiki.parseGroovyFromPage('XWiki.libProxyki'))
#set($con=$lib.load("XObj"))
$con.connect($xwiki,$context)
#set($langs=$xwiki.getXWikiPreference("languages").split(","))
#set($res="")
#foreach($lang in $langs)
#set($res="$res${con.getLanguageId($lang,true)} ")
#end
$res
#end