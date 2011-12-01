package org.curriki.tools.monitor;

import org.apache.commons.exec.*;

import java.io.*;
import java.util.logging.StreamHandler;


public class MonitorAllSources {

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
        errorStream = new FileOutputStream("output/errors.log");

        try {
            // identify appserv pid (for now form CLI)
            //new MyBackgroundProcess("echo \"testing, testing\"", null, 2);

            /*
            DefaultExecutor executor = new DefaultExecutor();
            OutputStream out = new ByteArrayOutputStream(512);
            executor.setStreamHandler(new PumpStreamHandler(out, null));
            int success = executor.execute(
                    new CommandLine("ssh").addArgument("appserv@prod-app").addArgument(
                            "/usr/java/bin/jps -l | /usr/bin/egrep com.sun.enterprise.server.PELaunch | /usr/bin/awk '{print $1}'"));
            if(success!=0) throw new IllegalStateException("Could not find process.");
            String processNum = out.toString().trim(); */
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
            System.out.println("--- Processes launched for one minute.");


            // send regular QUIT signals
            threadDumpRequestor = new RegularLauncher("ssh appserv@prod-app /appserv/threadDump.sh", 12, 5000, null, null);
            Thread.sleep(60000);

            // now launch stream analyzers
            MonitorWebRenderer.main(new String[] {processNum});

        } finally {
            /* if(apacheLogTailer!=null) apacheLogTailer.finish();
            if(appservTailer!=null) appservTailer.finish();
            if(topCollector!=null) topCollector.finish();
            if(threadDumpRequestor!=null) threadDumpRequestor.stop(); */
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

