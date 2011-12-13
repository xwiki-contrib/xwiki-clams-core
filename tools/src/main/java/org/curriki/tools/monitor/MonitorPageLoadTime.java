package org.curriki.tools.monitor;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;

import java.io.*;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MonitorPageLoadTime {

    public static final int INTERVAL = 5000, MAX_TIMES = 36;

    private final HttpClient httpClient;
    private final GetMethod get;
    private final Thread runner;
    private boolean timeToStop = false;
    private DateFormat df = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");

    public MonitorPageLoadTime(final String url, final PrintWriter out) {
        this.httpClient = new HttpClient();
        this.get = new GetMethod(url);
        this.runner = new Thread() {
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
        File baseDir = new File(new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date()));
        baseDir.mkdirs();
        for(String u: args) {
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(
                            new File(baseDir,URLEncoder.encode(u.substring("http://".length())))),"utf-8"));
            new MonitorPageLoadTime(u, out);
        }
    }
    
}
