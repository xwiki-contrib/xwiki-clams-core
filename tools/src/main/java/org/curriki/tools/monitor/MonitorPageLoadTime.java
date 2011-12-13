package org.curriki.tools.monitor;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.*;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class MonitorPageLoadTime implements MonitoringConstants {

    public static final int INTERVAL = 5000, MAX_TIMES = 36;

    private final HttpClient httpClient;
    private final GetMethod get;
    private final Thread runner;
    private boolean timeToStop = false;
    private DateFormat df = new SimpleDateFormat(PAGE_LOAD_DATEFORMAT_PATTERN);
    private static ThreadGroup threadGroup = new ThreadGroup("PageLoadTimes");

    public MonitorPageLoadTime(final String url, final PrintWriter out) {
        this.httpClient = new HttpClient();
        this.get = new GetMethod(url);
        this.runner = new Thread(threadGroup, "PageLoader") {
            public void run() {
                long start = System.currentTimeMillis();
                int k=0;
                while(!timeToStop) {
                    try {
                        long thisStart = System.currentTimeMillis();
                        int status = httpClient.executeMethod(get);
                        k++;
                        if(k>=MAX_TIMES) break;
                        long end = System.currentTimeMillis();
                        out.println("[" + df.format(new Date(start)) + "] " + (end - thisStart) / 1000f + " s " + url + " : " + get.getStatusLine());
                        if(k%3==0) out.flush();
                        long sleepTime = 0;
                        while(sleepTime<=0) sleepTime = start+k*INTERVAL - System.currentTimeMillis();
                        Thread.sleep(sleepTime);
                        } catch (InterruptedException e) { timeToStop = true; System.err.println("Interrupted.");
                    } catch (IOException e) { e.printStackTrace(); }
                }
                try {
                    out.flush();
                    out.close();
                } catch (Exception e) { e.printStackTrace(); }
            }
        };
        runner.start();
    }

    protected void finalize() throws Throwable {
        super.finalize();
        timeToStop = true;
        runner.interrupt();
    }

    public static void main(String[] args) throws Exception {
        File baseDir = new File(System.getProperty("user.dir"));//new File(new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date()));
        baseDir.mkdirs();
        List<MonitorPageLoadTime> l = new LinkedList<MonitorPageLoadTime>();
        for(String u: URLS_TO_MONITOR) {
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(
                            new File(baseDir,urlToFile(u))),"utf-8"));
            l.add(new MonitorPageLoadTime(u, out));
        }
        while(true) {
            boolean alive = false;
            for(MonitorPageLoadTime mp: l) {
                if(mp.runner.isAlive()) {
                    alive = true;
                    break;
                }
            }
            if(!alive) System.exit(0);
            Thread.sleep(1000);
        }
    }


    static String urlToFile(String url) {
        return URLEncoder.encode(url.substring("http://".length()));
    }
}
