package org.curriki.tools.loganalyzer;

import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MultipleParallelConsolesTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        System.setProperty("user.lang","en");
        System.setProperty("user.local","en-US");
        lines = IOUtils.readLines(this.getClass().getResourceAsStream("/sample-log.log"));

    }

    List<String> lines;
    int numRequestsReported = 0;

    public void testMultipleParallelConsoles() throws Throwable {
        // plan a few producers
        int numLogs = (int) (Math.random()*10);
        System.err.println("Running with " + numLogs + " producers.");

        // create a bunch of LogProducers
        LogProducer[] producers = new LogProducer[numLogs];
        File baseDir = File.createTempFile("multipleParallelConsoles", "log").getAbsoluteFile().getParentFile();
        if(!baseDir.mkdirs() && !baseDir.isDirectory()) throw new IllegalStateException("Can't make directory.");
        for(int i=0; i<numLogs; i++) {
            producers[i] = new LogProducer(new File(baseDir, "multipleParallelConsoles-" + i + "-log"));
        }

        LogAnalysisCursor cursor = new LogAnalysisCursor("","",true) {
            @Override
            void handleLine(String ip, String timeS, String durationS, String user, String method, String path, String protocol, String statusS, String sizeS, String referer, String userAgent, String route) {
                super.handleLine(ip, timeS, durationS, user, method, path, protocol, statusS, sizeS, referer, userAgent, route);
                numRequestsReported++;
            }

            @Override
            protected void reportNumbers(long meanDuration, long meanSize, int numberOfRequests, int numberOf200, int numberOf304, int numberOfRedirects, int numberOfErrors, int numberOfGet, int numberOfPost,
                                         Map<String, Integer> numberByCategory, Map<String, Integer> numberByRoute,
                                         int numberOfRobots, int numberOfMobiles, int numberOfComputers, int numberOfUnknownDevice) {
                super.reportNumbers(meanDuration, meanSize, numberOfRequests, numberOf200, numberOf304, numberOfRedirects, numberOfErrors, numberOfGet, numberOfPost,
                        numberByCategory, numberByRoute,
                            numberOfRobots, numberOfMobiles, numberOfComputers, numberOfUnknownDevice);
                System.err.println("reporting: " + numberOfRequests);
            }
        };
        // 10 s before the start so there's a chance the whole processing is in time.
        cursor.setFakeStartTime(new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss ZZZZZ", new Locale("en","US")).parse("15/Apr/2014:15:10:43 -0700").getTime());
        cursor.setPeriodicity(1000);

        // start fetching from all of them, catching the amount of things
        for(int i=0; i<numLogs; i++) {
            new LogCollector(baseDir.getAbsolutePath(),
                    "multipleParallelConsoles-" + i + "-log.*", true,
                    true, cursor);
        }

        Thread.sleep(30000);

        System.out.println("Expecting " + (numLogs * lines.size()) + " and obtained " + numRequestsReported + ".");
        assertTrue("There should be " + (numLogs * lines.size())
                + " requests reported (but there was " + numRequestsReported + " reported).", numRequestsReported == numLogs * lines.size());
    }

    private class LogProducer extends Thread {
        private LogProducer(File file) {
            super("LogProducer-" + file.getName());
            this.file = file;
            try {
                out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
                System.err.println(super.getName() + ": Changing file to " + file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            this.start();
        }
        private int number = 0;
        private File file;
        private Writer out;


        public void run() {
            long timeChanged = System.currentTimeMillis();
            for(String line: lines) {
                System.err.println("THREAD-" + super.getName() + " : " + line);
                try {
                    Thread.sleep(200 + (int) (Math.random()*500));
                    if(Math.random()<0.1f && System.currentTimeMillis()-timeChanged>10000) {
                        out.flush(); out.close();
                        file = new File(file.getParentFile(), file.getName() + number);
                        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
                        System.err.println(super.getName() + ": Changing file to " + file);
                        timeChanged = System.currentTimeMillis();
                    }
                    out.write(line);
                    out.write("\n");
                    out.flush();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
