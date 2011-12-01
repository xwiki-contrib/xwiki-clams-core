/**
 * Javascript tool to scroll-roll the log to the right time or to zap to the right page.
 * ----
 * This script's function rollToTime  assumes the presence of a variable
 * Curriki.monitor.timeToAnchor associating strings of time to anchor-names
 * present in the page.
 */


window.baseLocation = window.location.href;

Curriki = new Object();
Curriki.monitor = new Object();

Curriki.monitor.rollToTime =  function(fromStartInSeconds) {
// =================================================================
    var key = "" + fromStartInSeconds + "";
    var anchor  = Curriki.monitor.timeToAnchor[key];
    if(console) console.log("Would jump to " + fromStartInSeconds + " i.e. anchor " + anchor);
    window.location.href=baseLocation+ "#" + anchor;
    //window.scrollTo($.scrollLeft(), $("#" + anchor).y);
    //$("#" + anchor).show();
}


Curriki.monitor.jumpToTimePage =  function(fromStartInSeconds, stepLength) {
// =================================================================
    console.log("stepLength = " + stepLength);
    var fileNum = Math.round(fromStartInSeconds/stepLength);
    var fileName = baseLocation.replace(/_[0-9]+/,"_"+fileNum+"");
    console.log("Would jump to time " + fromStartInSeconds + " i.e. file " + fileName + ".");
    window.location.href=fileName;
}


Curriki.monitor.foldUnfold = function(elt) {
	console.log('Fold unfold on ', elt);
	var divElt = null;
	for(i=0; i<elt.childNodes.length; i++)
		if(elt.childNodes[i].tagName == 'DIV') divElt = elt.childNodes[i];
	var display = divElt.style.display;
	if(display=='block') divElt.style.display = 'none';
	if(display=='none') divElt.style.display = 'block';
}


