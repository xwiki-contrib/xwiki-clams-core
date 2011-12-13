package org.curriki.tools.monitor;

public interface MonitoringConstants {

    public static final String[] URLS_TO_MONITOR = new String[] {
            "http://www.curriki.org/",
            "http://www.curriki.org/xwiki/bin/view/Main/About",
            "http://www.curriki.org/xwiki/bin/view/Coll_Group_LosAngelesCommunityCollegesDevelopmentalMathExchange/AlternateCourseFormats",
            "http://www.curriki.org/xwiki/bin/view/Coll_jmarks/Frogjumpinglogicpuzzle"};

    public static final String PAGE_LOAD_MEASURE_BASE =
        "http://atlas.hoplahup.net/monitoring/currikiPageLoadTimes/";


    public static final String
        PAGE_LOAD_DATEFORMAT_PATTERN = "dd/MM/yyyy:HH:mm:ss Z",
        APACHE_LOG_DATEFORMAT="dd/MMM/yyyy:HH:mm:ss Z",
        APPSERV_LOG_DATEFORMAT="yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        DIRNAME_DATEFORMAT = "yyyy-MM-dd_HH-mm-ss-SSS_Z";

}
