package org.curriki.tools.loganalyzer;

import java.util.Map;

public class MaximizingLogAnalysisCursor extends LogAnalysisCursor {

    public MaximizingLogAnalysisCursor(String zabbixCli, String zabbixHost, boolean automatic) {
        super(zabbixCli, zabbixHost, automatic);
    }


    long meanDuration=0, meanSize=0;
    int numberOfRequests=0, numberOf200=0, numberOf304=0, numberOfRedirects=0, numberOfErrors=0, numberOfGet=0, numberOfPost=0;
    int numberOfRobots=0, numberOfMobiles=0, numberOfComputers=0, numberOfUnknownDevice=0;


    @Override
    protected void reportNumbers(long meanDuration, long meanSize, int numberOfRequests, int numberOf200, int numberOf304, int numberOfRedirects, int numberOfErrors, int numberOfGet, int numberOfPost,
                                 Map<String, Integer> numberByCategory, Map<String, Integer> numberByRoute,
                                 int numberOfRobots, int numberOfMobiles, int numberOfComputers, int numberOfUnknownDevice) {
        super.reportNumbers(meanDuration, meanSize, numberOfRequests, numberOf200, numberOf304, numberOfRedirects, numberOfErrors, numberOfGet, numberOfPost,
                numberByCategory, numberByRoute, numberOfRobots, numberOfMobiles, numberOfComputers, numberOfUnknownDevice);
        this.meanDuration = Math.max(meanDuration, this.meanDuration);
        this.numberOfRequests = Math.max(numberOfRequests, numberOfRequests);
        this.meanSize = Math.max(meanSize, this.meanSize);
        this.numberOf200 = Math.max(numberOf200, this.numberOf200);
        this.numberOf304 = Math.max(numberOf304, this.numberOf304);
        this.numberOfRedirects = Math.max(numberOfRedirects, this.numberOfRedirects);
        this.numberOfErrors = Math.max(numberOfErrors, this.numberOfErrors);
        this.numberOfGet = Math.max(numberOfGet, this.numberOfGet);
        this.numberOfPost = Math.max(numberOfPost, this.numberOfPost);
        this.numberOfRobots = Math.max(numberOfRobots, this.numberOfRobots);
        this.numberOfMobiles = Math.max(numberOfMobiles, this.numberOfMobiles);
        this.numberOfComputers = Math.max(numberOfComputers, this.numberOfComputers);
        this.numberOfUnknownDevice = Math.max(numberOfUnknownDevice, this.numberOfUnknownDevice);
    }
}
