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



/* function clickedOnPlot(ev, gridpos, datapos, neighbor, plot) {
     var x = datapos.xaxis, y = datapos.yaxis;
     x = Math.round(x/granularity)*granularity;
     console.log("click!", x);
    Curriki.monitor.renewPlot(x, null, null);
    positionWindows(x);
}
$.jqplot.eventListenerHooks.push(['jqplotClick', clickedOnPlot]);

$.jqplot('chartdiv',  [cpuLoad],
    { title:'Load',
        axes:{xaxis:{min:0, max:60}, yaxis:{min:0, max:maxCpuLoad}},
        series:[{color:'#5FAB78', title: "Appserv CPU"}]
    });

function adjustTops(p) {
    jQuery.ajax({ context:document.body,
                url: "topsHeader_" + p + ".txt",
                success: function(data) {
                  //console.log("Data received: " + data);
                  $('#topHeader').html(data);
                }});
}

function positionWindows(p) {
    p = Math.round(p/granularity)*granularity;
    adjustTops(p);
    frames['apacheLogs'].Curriki.monitor.rollToTime(Math.round(p));
    frames['appservLogs'].Curriki.monitor.rollToTime(Math.round(p));
    frames['threads'].Curriki.monitor.jumpToTimePage(Math.round(p), granularity);
    frames['mysql'].Curriki.monitor.jumpToTimePage(Math.round(p), granularity);
}

*/

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
    for(i=0; i<duration/granularity; i++) { dataSet[i]=new Array(i*granularity, 0); }
    $("span").each(function(pos, elt) {
        elt = $(elt);
        if(regexp.test(elt.text())) {
            elt.css("background-color","yellow");
            var t = elt.attr("time");
            var time = parseInt(t);
            if(time) {
                var point = dataSet[time];
                if(typeof(point)=="object")
                    dataSet[time] = [point[0], point[1]+1];
                //console.log("dataSet["+time+"]=" + dataSet[time]);
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
        otherD = Curriki.monitor.olderDataSeries;
        otherDataLabel = Curriki.monitor.olderDataLabel;
    } else {
        var p = Curriki.monitor.normalizeSeries(otherDataSeries);
        otherD = p[1];
        maxOtherD = p[0];
        if(maxOtherD!=0) otherDataLabel =  otherDataLabel + " (0-" + maxOtherD + ")";
        Curriki.monitor.olderDataSeries = otherD;
        Curriki.monitor.olderDataLabel = otherDataLabel;
    }
    // clear
    document.getElementById("chartdiv").innerHTML = "";

    var dataSets = [cpuLoad, otherD, clickData];
    var labels = new Array('Appserv CPU',
                                otherDataLabel,
                                clickedLabel);
    var seriesOptions = new Array(
        {title: "Appserv CPU",      color:'#5FAB78'},
        {color:'#111111', lineWidth: '0.3px', shadow:false },
        {color:'brown', lineWidth: '1px'}, // clicks
        {color:'#AAAAAA', lineWidth: '0.3px', shadow:false} // cache evictions
        )
    if(typeof(cacheEvictions)=="object") {
        if(typeof(cacheEvictions.normalized)=="boolean") {
            // nothing to do
        } else {
            cacheEvictions = Curriki.monitor.normalizeSeries(cacheEvictions);
            cacheEvictions.normalized = true;
        }
        dataSets.push(cacheEvictions[1]);
        labels.push("Cache evictions (0-" + Math.round(cacheEvictions[0]/100)*100 + ")");
    }

    // chart
    d = $.jqplot('chartdiv',
            dataSets,
            { axes:{xaxis:{min:0, max:maxTime}, yaxis:{min:0, max:maxCpuLoad}},
            legend:{ show:true,
                labels: labels },
            series:seriesOptions    });
}

Curriki.monitor.normalizeSeries = function(otherDataSeries) {
    var otherD = new Array();
    var maxOtherD = 0;
    // compute max
    for(i=0; i<otherDataSeries.length; i++)
        { if(otherDataSeries[i][1]>maxOtherD) maxOtherD = otherDataSeries[i][1]; }
    for(i=0; i<otherDataSeries.length; i++) {
        var x = otherDataSeries[i];
        var val = x[1]*maxCpuLoad/maxOtherD;
        if(isNaN(val)) val = 0;
        otherD.push([x[0], val]);
    }
    return [maxOtherD, otherD]
}