/**
 * Javascript tool to scroll-roll the log to the right time or to zap to the right page.
 * ----
 * This script's function rollToTime  assumes the presence of a variable
 * Curriki.monitor.timeToAnchor associating strings of time to anchor-names
 * present in the page.
 */


window.baseLocation = window.location.href;

maxTime = 60;

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
    var fileName = baseLocation.replace(/_[0-9]+\.html/,"_"+fileNum+".html");
    if(console) console.log("Would jump to time " + fromStartInSeconds + " i.e. file " + fileName + ".");
    window.location.href=fileName;
};


Curriki.monitor.foldUnfold = function(elt) {
	if(console) console.log('Fold unfold on ', elt);
	var divElt = null;
	for(i=0; i<elt.childNodes.length; i++)
		if(elt.childNodes[i].tagName == 'DIV') divElt = elt.childNodes[i];
	var display = divElt.style.display;
	if(display=='block') divElt.style.display = 'none';
	if(display=='none') divElt.style.display = 'block';
};



Curriki.monitor.searchALog = function(query, frameName) {
    try {
        if (typeof(query) == "object") {
            window.lastQuery = query;
            query = $(query).children("input").val();
        }
        var searchMatchTimes = window.frames[frameName].Curriki.monitor.searchInLog(query, duration, granularity);
        Curriki.monitor.renewPlot(null, searchMatchTimes, query);
    } catch(e) {if(console) console.log(e);}
};

Curriki.monitor.searchInLog = function(query, duration, granularity) {
    var regexp = new RegExp(query);
    var dataSet = new Array();
    for(i=0; i<duration; i+=granularity) {dataSet.push([i, 0]);}
    $("span").each(function(pos, elt) {
        elt = $(elt);
        if(regexp.test(elt.text())) {
            elt.css("background-color","yellow");
            var time = elt.attr("time");
            if(time) {
                time = Math.floor(time/granularity)*granularity;
                var i= time/granularity;
                var point = dataSet[i];
                dataSet[i] = [i*granularity, point[i]+1];
                if(console) console.log("dataSet["+i+"]=" + dataSet[i]);
            }
        } else {
            elt.css("background-color","transparent");
        }
    });
    return dataSet;
};


Curriki.monitor.olderDataSeries = [];
Curriki.monitor.olderDataLabel = " ";
Curriki.monitor.olderTimeClicked = null;

Curriki.monitor.renewPlot = function(timeClicked, otherDataSeries, otherDataLabel) {
    var clickData = [], clickedLabel = " ";
    if(timeClicked==null) timeClicked = Curriki.monitor.olderTimeClicked;
    if(typeof(timeClicked)=="number") {
        clickData = [[timeClicked, 0], [timeClicked, maxCpuLoad*0.9]];
        clickedLabel = 'clicked: '+timeClicked+'s';
        Curriki.monitor.olderTimeClicked = timeClicked;
    }
    var otherD = [];
    var maxOtherD = 0;

    if(typeof(otherDataSeries)=="undefined" || otherDataSeries==null) {
        otherDataSeries = Curriki.monitor.olderDataSeries;
        otherDataLabel = Curriki.monitor.olderDataLabel;
    } else {
        for(x in otherDataSeries) { if(x[1]>maxOtherD) maxOtherD = x[1]; }
        for(x in otherDataSeries) {
            var val = x[1]*maxCpuLoad/maxOtherD;
            if(isNaN(val)) val = 0;
            otherD.push([x[0], val]);
        }
        if(maxOtherD!=0) otherDataLabel =  otherDataLabel + " (0-" + maxOtherD + ")";
        Curriki.monitor.olderDataSeries = otherD;
        Curriki.monitor.olderDataLabel = otherDataLabel;
    }
    // clear
    document.getElementById("chartdiv").innerHTML = "";
    // chart
    d = $.jqplot('chartdiv',
           [cpuLoad, otherD, clickData],
            { axes:{xaxis:{min:0, max:maxTime}, yaxis:{min:0, max:maxCpuLoad}},
            legend:{ show:true,
                    labels:[    'Appserv CPU',
                                otherDataLabel,
                                clickedLabel] },
            series:[{color:'#5FAB78', title: "Appserv CPU"}]    });
}