package org.curriki.tools.loganalyzer;

import cz.mallat.uasparser.UASparser;
import cz.mallat.uasparser.UserAgentInfo;

import java.io.IOException;
import java.text.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tool to receive log-lines and maintain current means that are sent to a command-line listener
 */
public class LogAnalysisCursor extends Thread {

    public LogAnalysisCursor(String zabbixCli, String zabbixHost, boolean automatic) {
        this.zabbixCli = zabbixCli;
        this.zabbixHost = zabbixHost;
        this.timeFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss ZZZZZ", new Locale("en-US"));
        this.numberFormat = new DecimalFormat("##########");
        try {
            parser = new UASparser(this.getClass().getResourceAsStream("/userAgentStrings.ini"));
        } catch (IOException e) {
            throw new IllegalStateException("No user Agent Strings.");
        }
        if(automatic) super.start();
    }
    private String zabbixCli, zabbixHost;
    private final DateFormat timeFormat;
    private final NumberFormat numberFormat;
    private static Pattern fileNamePattern;
    private long timeDifference = 0;
    private long periodicity = 10000;
    private final UASparser parser;

    void setFakeStartTime(long startTime) {
        timeDifference = startTime-System.currentTimeMillis();
    }
    void setPeriodicity(long periodicity) { this.periodicity = periodicity; }

    public void run() {
        while(true) {
            try {
                Thread.sleep(periodicity);
                calculateAndReport();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    static String readExtension(String path) {
        if(fileNamePattern==null) fileNamePattern = Pattern.compile("((/xwiki/bin/download/[^/]*/[^/]*/(.*))(\\?.*)?|(/xwiki/bin/[a-z]+/[^/]*/[^/]*)(\\?.*)?|(.*)\\.([a-zA-Z]+)(\\?.*)?|(.*)/)");
        Matcher m = fileNamePattern.matcher(path);
        String extension = null;
        try {
            if(!m.matches()) {
                System.err.println("Broken filename: " + path);
            } else if(m.group(3)!=null) {
                // e.g. /xwiki/bin/download/Main/WebHome/xx.gif
                extension = m.group(3);
            } else if(m.groupCount()>=5 && m.group(5)!=null) {
                // e.g. /xwiki/bin/view/Main/WebHome
                extension = "html";
            } else if(m.groupCount()>=8 && m.group(8)!=null) {
                // e.g. /xwiki/skins/xx/x.png
                extension = m.group(8);
            }
        } catch (Exception e) {
            // something happened with the regexp, just report and keep things null
            e.printStackTrace();
        }
        return extension;
    }


    void handleLine(String ip, String timeS, String durationS, String user, String method, String path, String protocol, String statusS, String sizeS, String referer, String userAgent, String route) {
        try {
            // parsing ops
            Date time;
            synchronized(timeFormat) {
                time =  timeFormat.parse(timeS);
            }
            long duration, size;
            int status;
            synchronized(numberFormat) {
                duration = numberFormat.parse(durationS).longValue();
                size = "-".equals(sizeS) ? 0 : numberFormat.parse(sizeS).longValue();
                status = numberFormat.parse(statusS).intValue();
            }
            String extension = readExtension(path);
            //System.out.println("PATH: " + path);

            UserAgentInfo userAgentInfo = parser.parse(userAgent);
            //System.err.println("Extension: " + extension);
            handleLog(time, duration, status, size, null, extension, user, method, route, userAgent, userAgentInfo);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Broken line: time: " + timeS + " duration: " + durationS + " path: " + path);
        }


    }


    private void handleLog(Date time, long duration, int status, long size, String fileName, String extension, String user, String httpMethod, String route, String userAgentString, UserAgentInfo userAgentInfo) {
        Event event = new Event(time, duration, status, size, fileName, extension, user, httpMethod, route, userAgentString, userAgentInfo);
        insertEvent(event);
    }

    /*
      -- window of 1 minute
      -- delay of 10 s
      -- put events in a linkedList, in order of start time
      -- make measures on the current window
      -- an event is in the current window if its start-time is more than 10s ago, and its end-time is after 70s ago
     */

    private class Event {
        private final long startTime, endTime;
        private final long size, duration;
        private int status;
        private String fileName, extension, user, httpMethod, category, route, userAgentString;
        private UserAgentInfo userAgentInfo;

        public Event(Date time, long duration, int status, long size, String fileName, String extension, String user, String httpMethod, String route, String userAgentString, UserAgentInfo userAgentInfo) {
            this.startTime = time.getTime();
            this.endTime = startTime + duration/1000;
            this.duration = duration;
            this.status = status;
            this.size = size;
            this.fileName = fileName;
            this.extension = extension;
            this.category = guessCategory(extension);
            this.user = user;
            this.httpMethod = httpMethod;
            this.route = route;
            this.userAgentInfo = userAgentInfo;
            this.userAgentString = userAgentString;
        }

        private String guessCategory(String extension) {
            if(extension==null || extension.length()==0) return "none";
            String cat = categoryByExtension.get(extension.toLowerCase());
            if(cat==null) return "none";
            return cat;
        }
    }

    final LinkedList<Event> events =new LinkedList<Event>();
    static final Set<String> personalComputerOSs = new HashSet(Arrays.asList("OS X", "Windows", "Linux")),
        mobileOSs = new HashSet(Arrays.asList("iOS", "Android", "Blackberry"));

    private void insertEvent(Event e) {
        synchronized(events) {
            events.add(e);
        }
    }

    /**
     * Removes events whose end date is before 70 s ago
     */
    private void purgeEvents() {
        long limit = System.currentTimeMillis()-70000L + timeDifference;
        synchronized (events) {
            Iterator<Event> it = events.iterator();
            while(it.hasNext()) {
                Event e = it.next();
                if(e.endTime < limit) it.remove();
            }
        }
    }

    void calculateAndReport() {

        if(events.isEmpty()) return;

        long meanDuration, meanSize;
        int numberOfRequests=0, numberOf200=0, numberOf304=0, numberOfRedirects=0, numberOfErrors=0, numberOfGet=0, numberOfPost=0;
        Map<String, Integer> numberByCategory = new HashMap<String, Integer>(), numberByRoute= new HashMap<String, Integer>();


        // set limits
        long intervalEnd = System.currentTimeMillis()-10000+timeDifference;
        purgeEvents();

        // mean duration
        long m = 0;
        int count = 0;
        synchronized (events) {
            for(Event e: events) {
                if(e.startTime > intervalEnd) continue;
                count++;
                m += e.duration;
            }
        }
        meanDuration = count==0 ? 0 :  m/count;

        // mean size
        m = 0; count = 0;
        synchronized (events) {
            for(Event e: events) {
                if(e.startTime > intervalEnd) continue;
                count++;
                m += e.size;
            }
        }
        meanSize = count==0 ? 0 : m/count;

        // numberOfRequests
        numberOfRequests = count;


        // numberOf304 and numberOfRedirects
        count = 0;
        synchronized (events) {
            for(Event e: events) {
                int hundreds = e.status/100;
                if(hundreds==3 && e.startTime > intervalEnd) {
                    if(e.status==304) numberOf304++;
                    else numberOfRedirects++;
                }
                if(e.status==200) numberOf200++;
                if(hundreds== 4 || hundreds==5) numberOfErrors++;
            }
        }


        // numberByCategory
        for(String category: categories) numberByCategory.put(category, 0);
        synchronized (events) {
            for(Event e: events) {
                int cc = numberByCategory.get(e.category);
                numberByCategory.put(e.category, ++cc);
            }
        }

        // numberByRoute
        synchronized (events) {
            for(Event e: events) {
                if(!(numberByRoute.containsKey(e.route)))
                    numberByRoute.put(e.route, 0);
                numberByRoute.put(e.route, numberByRoute.get(e.route)+1);
                //System.err.println("NumberByRoute: " + e.route + numberByRoute.get(e.route));
            }
        }

        // numberOfGet, numberOfPost
        numberOfGet = 0; numberOfPost = 0;
        synchronized(events) {
            for(Event e: events) {
                if(e.startTime > intervalEnd) continue;
                if("GET".equals(e.httpMethod)) numberOfGet++;
                if("POST".equals(e.httpMethod)) numberOfPost++;
            }

        }

        // robots/computer/portable
        int numberOfRobots=0, numberOfMobiles=0, numberOfComputers=0, numberOfUnknownDevice=0;
        synchronized(events) {
            for(Event e: events) {
                if (e.startTime > intervalEnd) continue;
                UserAgentInfo ua = e.userAgentInfo;
                if (ua.isRobot() || e.userAgentString.toLowerCase().contains("bot"))
                    numberOfRobots++;
                else if ("Personal computer".equals(ua.getDeviceType()) ||
                        personalComputerOSs.contains(ua.getOsFamily())) {
                    numberOfComputers++;
                } else if ("Tablet".equals(ua.getDeviceType()) || "Smartphone".equals(ua.getDeviceType())
                        || mobileOSs.contains(ua.getOsFamily())) {
                    numberOfMobiles++;
                } else {
                    numberOfUnknownDevice++;
                }
            }
        }


        reportNumbers(meanDuration, meanSize, numberOfRequests, numberOf200, numberOf304, numberOfRedirects, numberOfErrors, numberOfGet, numberOfPost,
                numberByCategory, numberByRoute,
                numberOfRobots, numberOfMobiles, numberOfComputers, numberOfUnknownDevice);
        System.out.flush();


        // TODO: number of users
        // TODO: number of robot requests
        // number of sessions?

        // - currikiWeb[localhost,keyName]



        /*


            beta-web apache[localhost,ReadingRequest] 0
            beta-web apache[localhost,Closingconnection] 0
            beta-web apache[localhost,SendingReply] 1
            beta-web apache[localhost,Startingup] 0
            beta-web apache[localhost,Idlecleanupofworker] 0
            beta-web apache[localhost,DNSLookup] 0
            beta-web apache[localhost,WaitingforConnection] 9
            beta-web apache[localhost,Keepaliveread] 0
            beta-web apache[localhost,IdleWorkers] 9
            beta-web apache[localhost,Logging] 0
            beta-web apache[localhost,BusyWorkers] 1
            beta-web apache[localhost,Gracefullyfinishing] 0
            beta-web apache[localhost,Openslotwithnocurrentprocess] 246
         */
    }

    protected void reportNumbers(long meanDuration, long meanSize, int numberOfRequests, int numberOf200, int numberOf304, int numberOfRedirects, int numberOfErrors, int numberOfGet, int numberOfPost,
                                 Map<String, Integer> numberByCategory, Map<String, Integer> numberByRoute,
                                 int numberOfRobots, int numberOfMobiles, int numberOfComputers, int numberOfUnknownDevice) {
        System.out.println("- currikiWeb[localhost,RequestDuration] " + meanDuration);
        System.out.println("- currikiWeb[localhost,MeanSize] "        + meanSize);
        System.out.println("- currikiWeb[localhost,NumberOfRequests] " + numberOfRequests);
        System.out.println("- currikiWeb[localhost,NumberOfStatus200] " + numberOf200);
        System.out.println("- currikiWeb[localhost,NumberOfStatus304] " +  numberOf304);
        System.out.println("- currikiWeb[localhost,NumberOfRedirects] " + numberOfRedirects);
        System.out.println("- currikiWeb[localhost,NumberOfErrorStatus] " + numberOfErrors);
        System.out.println("- currikiWeb[localhost,NumberOfGetRequests] " + numberOfGet);
        System.out.println("- currikiWeb[localhost,NumberOfPosts] " + numberOfPost);
        System.out.println("- currikiWeb[localhost,numberOfRobots] " + numberOfRobots);
        System.out.println("- currikiWeb[localhost,numberOfMobiles] " + numberOfMobiles);
        System.out.println("- currikiWeb[localhost,numberOfComputers] " + numberOfComputers);
        System.out.println("- currikiWeb[localhost,numberOfUnknownDevice] " + numberOfUnknownDevice);
        for(String category: categories) {
            System.out.println("- currikiWeb[localhost,NumberOfRequestsInCategory-" + category + "] " + numberByCategory.get(category));
        }

        for(String route: numberByRoute.keySet()) {
            Object o = numberByRoute.get(route); if(o==null) o = 0;
            System.out.println("- currikiWeb[localhost,NumberOfRequestsRouted_" + route+ "] " + o);
        }
    }

    private List<String> categories = Arrays.asList("none", "image", "js", "doc", "css", "ico");

    private Map<String, String> categoryByExtension = createCategoryByExtension();
    private Map<String,String> createCategoryByExtension() {
        Map<String,String> m = new HashMap<String,String>();
        m.put("png",  "image");
        m.put("jpg",  "image");
        m.put("jpeg", "image");
        m.put("pdf",  "doc");
        m.put("ico",  "ico");
        m.put("doc",  "doc");
        m.put("js",   "js");
        return m;
    }




}
