package org.curriki.tools.monitor;

import org.apache.commons.exec.*;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;


public class MonitorAllSources implements MonitoringConstants {

    private static final int duration = 60*1000;

    private static String userName = "polx";

    public static void main(String[] args) throws Exception {

        new MonitorAllSources(args[0]);

    }

    private File baseDir;
    private MyBackgroundProcess apacheLogTailer, appservTailer, topCollector;
    private RegularLauncher threadDumpRequestor;

    private OutputStream errorStream;


    private MonitorAllSources(String processNum) throws Exception {
        baseDir = new File(System.getProperty("user.dir"));
        new File(baseDir, "output").mkdirs();
        errorStream = new FileOutputStream("output/errors.log");

        try {
            System.out.println("--- Using appserv process number " + processNum + ".");

            // launch log collectors
            apacheLogTailer = new MyBackgroundProcess(
                    "ssh appserv@prod-web tail -f /opt/coolstack/apache2/logs/www.curriki.org_access_log",
                    "apacheLog.txt", duration);
            apacheLogTailer.start();

            appservTailer = new MyBackgroundProcess(
                    "ssh appserv@prod-app tail -f /appserv/nohup.out",
                    "appservLogs.txt", duration);
            appservTailer.start();

            topCollector =  new MyBackgroundProcess(
                    "ssh appserv@prod-app /usr/sfw/bin/top -U appserv -s 1 -n -d 60",
                    "tops.txt", duration);
            topCollector.start();

            // send regular show process list
            threadDumpRequestor = new RegularLauncher(
                    "ssh polx@prod-db /export/home/polx/showProcessList.sh",
                    12, 5000, "mysqlProcessList.txt","----------------------");

            MonitorCacheEviction monitorCacheEviction = new MonitorCacheEviction(new File("cacheEvictions.js"));
            monitorCacheEviction.start();

            // send regular QUIT signals
            threadDumpRequestor = new RegularLauncher("ssh appserv@prod-app /appserv/threadDump.sh", 12, 5000, null, null);


            System.out.println("--- Processes launched for one minute, waiting two minutes more.");
            Thread.sleep(120000);

            fetchPageLoadTimes();

            // now launch stream analyzers
            System.out.println("--- Now rendering.");
            MonitorWebRenderer.main(new String[] {processNum});

        } finally {
            /* if(apacheLogTailer!=null) apacheLogTailer.finish();
            if(appservTailer!=null) appservTailer.finish();
            if(topCollector!=null) topCollector.finish();
            if(threadDumpRequestor!=null) threadDumpRequestor.stop(); */
        }
    }

    static void fetchPageLoadTimes() {
        HttpClient client = new HttpClient();
        String[] urls = URLS_TO_MONITOR;

        byte[] b= new byte[512];
        for(String url: urls) {
            try {
                url = MonitorPageLoadTime.urlToFile(url);
                GetMethod method = new GetMethod(PAGE_LOAD_MEASURE_BASE + URLEncoder.encode(url));
                client.executeMethod(method);
                int i=0;
                InputStream in = method.getResponseBodyAsStream();
                FileOutputStream out = new FileOutputStream(url);
                while((i=in.read(b,0,512))!=-1) {
                    out.write(b,0,i);
                }
                out.flush();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}


class MyBackgroundProcess extends Thread {
    final Executor executor;
    final ExecuteWatchdog watchdog;
    final ExecuteStreamHandler streamHandler;
    OutputStream out;
    final String outputFile;
    final String cli;
    boolean finished = false;

    MyBackgroundProcess(String cli, String outputFile, long duration) throws IOException {
        this.cli = cli;
        watchdog = new ExecuteWatchdog(duration);
        executor = new DefaultExecutor();
        executor.setWatchdog(watchdog);
        executor.setExitValues(new int[]{0,255});
        this.outputFile = outputFile;
        if(outputFile!=null) out = new FileOutputStream(outputFile);//new ByteArrayOutputStream();
        else out = null;
        streamHandler = new PumpStreamHandler(new PrintStream(out), new PrintStream(out));
        executor.setStreamHandler(streamHandler);
    }

    public void run() {
        try {
            System.out.println("--- Launching " + cli);
            System.out.println("--- Output " + outputFile);
            int result = executor.execute( CommandLine.parse(cli));
            System.out.println("Result is " + result);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            finished = true;
            finish();
        }
    }

    void finish() {
        if(!finished) watchdog.stop();
        try {
            if(out instanceof ByteArrayOutputStream) {
                ByteArrayOutputStream bout = (ByteArrayOutputStream) out;
                out = new FileOutputStream(outputFile);
                System.out.println("Sending buffer of size " + bout.size() + ".");
                out.write(bout.toByteArray());
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            out.flush();
            out.close();
        } catch (IOException e) { e.printStackTrace(); }
    }
}

class RegularLauncher {
    Executor executor;
    int numTimes;
    final Thread thread;

    void stop() {
        thread.stop();
    }


    RegularLauncher(final String cmd, int numTimes, final int intervalMs, final String outputFile, final String separator) {
        this.numTimes = numTimes;
        thread = new Thread() { public void run() {
            try {
                OutputStream out = null;
                if(outputFile!=null) out = new FileOutputStream(outputFile);
                while (RegularLauncher.this.numTimes>0) {
                    executor = new DefaultExecutor();
                    if(out!=null) executor.setStreamHandler(new PumpStreamHandler(out));
                    executor.execute(CommandLine.parse(cmd));
                    if(out!=null)  {
                        out.write(separator.getBytes());
                        out.write((byte) '\n');
                        out.flush();
                    }
                    Thread.sleep(intervalMs);
                    RegularLauncher.this.numTimes--;
                }
                if(out!=null) out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } };
        thread.start();
    }


}



class MonitorCacheEviction extends Thread {

    public MonitorCacheEviction(File file) {
        this.file = file;
    }


    private File file;
    private HttpClient client = new HttpClient();
    private List<String[]> entries = new LinkedList<String[]>();
    int numRuns = 0, maxRuns = 12, interval = 5;
    long startTime;
    NumberFormat numForm = new DecimalFormat("####.###");

    public void run() {
        GetMethod get = new GetMethod("http://www.curriki.org/xwiki/bin/view/CurrikiCode/CacheMonitor");
        String prefix = "<li>eviction rate (ev/s):";
        startTime = System.currentTimeMillis();

        while(numRuns<maxRuns) {
            try {
                int status = client.executeMethod(get);
                if(status!=200) throw new IOException("Bad response from Curriki: " + get.getStatusLine());
                LineNumberReader in = new LineNumberReader(new InputStreamReader(get.getResponseBodyAsStream(),"utf-8"));
                String line;
                while((line=in.readLine())!=null) {
                    if(line.startsWith(prefix)) {
                        line = line.substring(prefix.length());
                        line = line.substring(0,line.indexOf('<'));
                        pushValue(line);
                    }
                }
                in.close();
                numRuns++; long timeout;
                while((timeout = startTime + numRuns * interval * 1000 - System.currentTimeMillis())<0)
                    numRuns++;
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        output(file);

    }

    private void pushValue(String value) {
        entries.add(new String[]{numForm.format(numRuns*interval), value});
    }

    public String getValues() {
        StringBuilder b = new StringBuilder();
        b.append("cacheEvictions = [");
        boolean started = false;
        for(String[] vals: entries) {
            if(started) b.append(", ");
            b.append("[").append(vals[0]).append(',').append(vals[1]).append("]");
            started = true;
        }
        b.append("];");
        return b.toString();
    }

    public void output(File file) {
        try {
            FileUtils.writeStringToFile(file, getValues());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}