package org.curriki.tools.loganalyzer;


import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogCollector {

    public LogCollector(String baseDir, String logFilePattern, boolean doResume, boolean debug, LogAnalysisCursor cursor) throws Exception {
        this.logFilePattern = Pattern.compile(logFilePattern);
        this.baseDir = new File(baseDir);
        this.currentFile = findLatestFile();
        tailer = createTailer(currentFile, doResume);
        fileRotator.start();
        queueProcessor.start();
        this.debug = debug;
        if(cursor==null)
            cursor = new LogAnalysisCursor("","", true);
        this.logAnalysisCursor = cursor;
    }

    private Tailer tailer;
    private Pattern logFilePattern;
    private File baseDir, currentFile;
    private boolean running = true;
    private boolean doResume;
    private long lastMeasuredLatest = 0;
    private long lastSeenLine = 0;
    private boolean debug;
    private static final long ONLY_CHANGE_EVERY_X_MS = 5000;
    private static final int LARGEST_QUEUE_SIZE = 65536;


    private Tailer createTailer(final File f, boolean resume) {
        Tailer t = new Tailer(f, tailerListener, 20, !resume) {
            @Override
            public void run() {
                if(debug) System.err.println("Starting to tail: " + f.getName());
                try {
                    super.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if(debug) System.err.println("Finished to tail: " + f.getName());
            }
        };

        Thread thread = new Thread(t, "Tailer-" + f.getName());

        thread.setDaemon(true);
        thread.start();
        return t;
    }

    private synchronized File findLatestFile() {
        File[] files = baseDir.listFiles();
        long lastModifMax = 0;
        File latestOne = null;
        for(File f: files) {
            if(logFilePattern.matcher(f.getName()).matches()) {
                long l = f.lastModified();
                if(l>lastModifMax) {
                    latestOne = f; lastModifMax = l;
                }
            }
        }
        lastMeasuredLatest = System.currentTimeMillis();
        if(debug) System.err.println("Pattern (" + logFilePattern + "): file: " + latestOne.getName());
        return latestOne;
    }


    private Thread fileRotator = new Thread("File Rotator") {
        @Override
        public void run() {
            long tick = System.currentTimeMillis();
            FileInputStream readFile = null;
            // implement the watchdog that  looks to change the file once every 10s.
            while(running) {
                try {
                    long time = System.currentTimeMillis();

                    // run every 100 ms
                    if(time-tick+100>0)
                        Thread.sleep(time-tick+100);
                    tick = time;

                    // has there been input in the last second?
                    if(time-lastSeenLine<1000)
                        continue; // then no need to scan for a changed file

                    // has there been a file-change recently? then maybe wait a bit
                    if(System.currentTimeMillis()-lastMeasuredLatest< ONLY_CHANGE_EVERY_X_MS)
                        continue;

                    // detect changed file
                    File elected = findLatestFile();
                    if(elected.equals(currentFile)) continue;

                    // create tailer
                    tailer.stop();
                    tailer = createTailer(elected, true);
                    currentFile = elected;

                    // swallow existing content first
                    // TODO: lock here
                    if(elected!=null) {
                        readFile = new FileInputStream(elected);
                        List<String> firstLines = org.apache.commons.io.IOUtils.readLines(readFile);
                        for(String s : firstLines) {
                            queueLine(s);
                        }
                    }

                } catch(Exception ex) {
                    ex.printStackTrace();
                } finally {
                    if(readFile!=null) {
                        try {
                            readFile.close();
                        } catch(Exception ex) {
                            // ignore
                        }
                    }
                }
            }
            try {
                if(tailer!=null) tailer.stop();
                if(queueProcessor!=null && queueProcessor.isAlive()) queueProcessor.stop();
                if(fileRotator!=null && fileRotator.isAlive()) fileRotator.stop();
            } catch(Throwable x) {
                x.printStackTrace();
            }
        }
    };



    private TailerListener tailerListener = new TailerListener() {
        public void init(Tailer tailer) {

        }

        public void fileNotFound() {

        }

        public void fileRotated() {

        }

        public void handle(String line) {
            lastSeenLine = System.currentTimeMillis();
            queueLine(line);
        }

        public void handle(Exception ex) {

        }
    };

    // ================== QUEUE ===================================================

    private ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<String>(LARGEST_QUEUE_SIZE+1);

    private void queueLine(String line) {
        if(debug) System.err.println("Queuing line: " + line);
        if(queue.size()>=LARGEST_QUEUE_SIZE) {
            // Pull alarm trigger
            new RuntimeException("Queue overflow.").printStackTrace();
            System.exit(0);
        }
        queue.add(line);
    }


    private Thread queueProcessor = new Thread("Queue Processor") {
        public void run() {
            while(running) {
                try {
                    String line = queue.poll();
                    if(line!=null) {
                        processLine(line);
                    }
                    synchronized(queue) {
                        if(queue.isEmpty()) queue.wait(1000);
                    }
                } catch (InterruptedException e) {
                    // no big issue here
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    // =================== LINE PROCESSING ============================================

    /*
        LogFormat "%h %l %u %t %D User:%{username}C \"%r\" %>s %b \"%{Referer}i\" \"%{User-Agent}i\"" combined

        (remote host, remote user, remote user, time the request was received, first line of request,
         status, size of response, refererer, user agent)

     Examples:
     69.145.82.248 - - [03/Apr/2014:14:37:30 -0700] 686 User:- "GET /wp-content/uploads/2012/05/read-more-small.png HTTP/1.1" 302 226 "http://www.curriki.org/?gclid=CPbp_pKjxb0CFU1bfgod2xcAUA" "Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 6.1; WOW64; Trident/4.0; SLCC2; .NET CLR 2.0.50727; .NET CLR 3.5.30729; .NET CLR 3.0.30729; Media Center PC 6.0; InfoPath.3; .NET4.0C; .NET4.0E)"
     69.144.182.1 - - [03/Apr/2014:14:37:32 -0700] 222424 User:- "GET /xwiki/bin/view/Search/ HTTP/1.1" 200 6435 "http://www.curriki.org/welcome/resources-curricula/" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.117 Safari/537.36"
     93.222.192.249 - - [03/Apr/2014:14:40:29 -0700] 869976 User:\"ChslXwq0Ofm9IQXsF6SbRQ__\" "GET /xwiki/skins/curriki20/extjs/ext-all-debug.js HTTP/1.1" 200 212596 "http://www.curriki.org/xwiki/bin/view/Coll_adminPolx/clustercheck" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36"

     63.241.109.8 - demo [04/Apr/2014:10:36:24 -0700] 10500470 User:- "GET / HTTP/1.0" 200 50200 "-" "Wget/1.11.4"
     93.222.235.200 - demo [04/Apr/2014:10:38:02 -0700] 190835 User:- "GET /xwiki/bin/view/Registration/MemberIFrame?xpage=plain HTTP/1.1" 200 633 "http://beta.curriki.org/" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36"


    180.76.5.169 - - [04/Apr/2014:10:48:46 -0700] 1139972 User:- "GET /xwiki/gen/js/72b9/TBD HTTP/1.1" 404 6303 "-" "Mozilla/5.0 (compatible; Baiduspider/2.0; +http://www.baidu.com/search/spider.html)"
   93.222.235.200 - demo [04/Apr/2014:11:11:28 -0700] 25620 User:- "GET /curricki/js//layerslider/skins/lightskin/skin.css HTTP/1.1" 404 207 "http://beta.curriki.org/" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36"
     */

    Pattern pattern = Pattern.compile("([0-9\\.]+) (-|demo) (-|demo) \\[([^\\]]+)\\] ([0-9]+) User:([^ ]+) \"([A-Z]+) ([^ ]+) ([^ ]*)\" ([0-9]*) ([0-9-]*) \"([^\"]*)\" \"([^\"]*)\" (.*)");


    final LogAnalysisCursor logAnalysisCursor;

    public void processLine(String line) {
        Matcher m = pattern.matcher(line);
        if(!m.matches()) {
            System.err.println("Non-matching-line:");
            System.err.println(line);
            return;
        }
        if(debug) {
            System.err.println("IP:         " + m.group(1));
            System.err.println("Time:       " + m.group(4));
            System.err.println("Duration:   " + m.group(5));
            System.err.println("User:       " + m.group(6));
            System.err.println("Method:     " + m.group(7));
            System.err.println("Path:       " + m.group(8));
            System.err.println("Protocol:   " + m.group(9));
            System.err.println("Status:     " + m.group(10));
            System.err.println("Size:       " + m.group(11));
            System.err.println("Referer:    " + m.group(12));
            System.err.println("User Agent: " + m.group(13));
            System.err.println("Route: "      + m.group(14));
        }

       logAnalysisCursor.handleLine(m.group(1), m.group(4), m.group(5), m.group(6), m.group(7), m.group(8), m.group(9), m.group(10), m.group(11), m.group(12), m.group(13), m.group(14) );
    }


}