package org.curriki.tools.monitor;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.helpers.AbsoluteTimeDateFormat;

import java.io.*;
import java.text.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** process to construct a set of HTML pages from the stand
 */
public class MonitorWebRenderer {

    private File baseDir = new File("output");
    private long start, end;
    private String pidAsString;

    private static final float intervalBetweenTops = 5000;
    private static DateFormat apacheLogDf = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z"),
        appservLogDf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        // example 2011-11-28T13:13:11.901-0800
    private static NumberFormat decimals = new DecimalFormat("######.##"),
        integers = new DecimalFormat("#####");


    public static void main(String[] args) throws Exception {
        MonitorWebRenderer mwr = new MonitorWebRenderer(apacheLogDf.parse("28/Nov/2011:13:13:13 -0800").getTime(),
                apacheLogDf.parse("28/Nov/2011:13:14:12 -0800").getTime(), "24521");
        mwr.topParser.parse();
        mwr.apacheLogParser.parse();
        mwr.appservLogParser.parse();
        mwr.mySQLProcessListSplitter.parse();
    }

    public MonitorWebRenderer(long start, long end, String pidAsString){
        this.start = start;
        this.end = end;
        this.pidAsString = pidAsString;
        baseDir.mkdirs();
        topParser = new TopParser();
        apacheLogParser = new ApacheLogParser();
        appservLogParser = new AppservLogParser();
        mySQLProcessListSplitter = new MySQLProcessListSplitter();
    }

    private TopParser topParser;
    private ApacheLogParser apacheLogParser;
    private AppservLogParser appservLogParser;
    private MySQLProcessListSplitter mySQLProcessListSplitter;


    private static class Util {
        private static String readTillStartsWith(LineNumberReader reader, String prefix, PrintWriter putLinesIn) throws IOException {
            String read;
            while((read = reader.readLine())!=null) {
                if(putLinesIn!=null) putLinesIn.println(read);
                if(read.startsWith(prefix)) return read;
            }
            return null;
        }

        private static void outputJsonPairsForJQPlot(PrintWriter out, String varName, float[] vals) {
            out.print(varName); out.print("= [");
            int numBuckets = vals.length;
            for(int i=0; i<numBuckets; i++) {
                out.print("[");
                out.print(integers.format((int) (i*intervalBetweenTops/1000)));
                out.print(", ");
                out.print(decimals.format(vals[i]));
                out.print("]");
                if(i+1<numBuckets) out.print(",");
            }
            out.println("];");
        }

        private static void outputJsonHash(PrintWriter out, String varName, int[] vals) {
            out.print(varName); out.print(" = {");
            int numBuckets = vals.length;
            for(int i=0; i<numBuckets; i++) {
                out.print("'");
                out.print(integers.format((int) (i*intervalBetweenTops/1000)));
                out.print("':'");
                out.print(integers.format(vals[i]));
                out.print("'");
                if(i+1<numBuckets) out.print(",");
            }
            out.println("};");
        }

        public static PrintWriter makePrintWriter(String fileName) throws IOException {
            System.err.println("--- outputting " + fileName);
            return new PrintWriter(new OutputStreamWriter(new FileOutputStream(fileName), "utf-8"));
        }
    }

    private class TopParser {
        float[] cpuLoads;
        int numBuckets;

        private TopParser() {
            numBuckets = (int) ((end-start)/intervalBetweenTops);
            cpuLoads = new float[numBuckets];
        }


        void parse() throws Exception {
            LineNumberReader in = new LineNumberReader(new InputStreamReader(new FileInputStream("tops.txt")));

            int count=0; String read = "";
            while(count< numBuckets) {

                read = Util.readTillStartsWith(in, "load averages", null);
                
                // first read the header
                PrintWriter out = Util.makePrintWriter("output/topsHeader_"+count+".txt");
                while((read=in.readLine())!=null && read.trim().length()>0) {
                    out.println(read);
                }

                // then read the first line (column headers
                read = in.readLine();
                out.println(read);

                // then find the line with the right pid and put it there
                while((read=in.readLine())!=null && read.trim().length()>0) {
                    if(read.trim().startsWith(pidAsString)) {
                        out.println(read);
                        collectCpuLoad(read, count);
                    }
                }
                out.flush(); out.close();
                count++;
            }

            // now output CPU load as a json;
            PrintWriter out = Util.makePrintWriter("output/topsCpuLoad.js");
            Util.outputJsonPairsForJQPlot(out, "cpuLoad", cpuLoads);
            out.flush(); out.close();
        }

        private void collectCpuLoad(String topLine, int count) throws Exception {
            String[] cols = topLine.split(" |\t");
            String cpuLoad = null;
            for(String col: cols) {
                if(col.endsWith("%"))
                    cpuLoad = col;
            }
            if(cpuLoad==null) return;
            cpuLoad = cpuLoad.substring(0, cpuLoad.length()-1);
            float load = decimals.parse(cpuLoad).floatValue();
            cpuLoads[count] = load;
        }
    }

    private class ApacheLogParser {

        TimeToLine ttl = new TimeToLine(intervalBetweenTops, start, end);

        void parse() throws Exception {
            PrintWriter out = Util.makePrintWriter("output/apacheLogs.html");
            String[] pageTemplateBits =
                    IOUtils.toString(this.getClass().getResourceAsStream("monitorResources/apacheLogsTemplate.html"), "utf-8")
                            .split("«|»");
            if(! ("log".equals(pageTemplateBits[1]) && "date".equals(pageTemplateBits[3]) && "data".equals(pageTemplateBits[5]) )) {
                throw new IllegalStateException("Template is malformed, expected sequence log, date, data.");
            }
            out.println(pageTemplateBits[0]);

            // put logs in
            LineNumberReader in = new LineNumberReader(new InputStreamReader(new FileInputStream("apacheLog.txt"),"utf-8"));
            String line;
            Pattern pattern = Pattern.compile("[^\\[]*\\[([^]]+)\\].*");
            int lineNum = 0;
            while( (line = in.readLine())!=null ) {
                if(line.trim().startsWith("|") || line.startsWith("Killed by signal"))
                    continue;
                try {
                    Matcher m = pattern.matcher(line);
                    if(m.matches()) {
                        ttl.add(apacheLogDf.parse(m.group(1)), lineNum);
                    } else
                        throw new IllegalStateException("Can't find date in line \"" + line + "\".");

                } catch (Exception e) {
                    System.err.println("Date mismatch in \"" + line + "\".");
                    e.printStackTrace();
                }
                out.print("<span id='");
                out.print(integers.format(lineNum));
                out.print("'>");
                out.print(line);
                out.println("</span>");
                lineNum++;
            }

            // footer
            out.print(pageTemplateBits[2]);
            out.print("Created on ");
            out.println(new Date());

            out.print(pageTemplateBits[4]);
            Util.outputJsonHash(out, "Curriki.monitor.timeToAnchor", ttl.buckets);
            out.print(pageTemplateBits[6]);

            out.flush();
            out.close();
            in.close();
        }
    }
    // =======================================================================================================
    private class AppservLogParser {

        TimeToLine ttl = new TimeToLine(intervalBetweenTops, start, end);
        int threadDumpNum = 0;

        void parse() throws Exception {
            PrintWriter out = Util.makePrintWriter("output/appservLogs.html");
            String[] pageTemplateBits =
                    IOUtils.toString(this.getClass().getResourceAsStream("monitorResources/appservLogsTemplate.html"), "utf-8")
                            .split("«|»");
            if(! ("log".equals(pageTemplateBits[1]) && "date".equals(pageTemplateBits[3]) && "data".equals(pageTemplateBits[5]) )) {
                throw new IllegalStateException("Template is malformed, expected sequence log, date, data.");
            }
            out.println(pageTemplateBits[0]);

            // put logs in
            LineNumberReader in = new LineNumberReader(new InputStreamReader(new FileInputStream("appservLogs.txt"),"utf-8"));
            String line;
            Pattern pattern = Pattern.compile("[^|]*\\|([^|]+)\\|.*");
            int lineNum = 0;
            while( (line = in.readLine())!=null ) {
                // a thread-dump? put it aside
                if(line.startsWith("Full thread dump")) {
                    PrintWriter pw = Util.makePrintWriter("output/threads_" + threadDumpNum + ".html");
                    pw.println("<html><head><title>Threads</title>" +
                            "    <script type='text/javascript' src='js/jquery.min.js'></script>\n" +
                            "    <script type='text/javascript' src='js/logRoller.js'></script>\n" +
                            "</head><body><pre>");
                    pw.println(line);
                    pw.println("</pre>\n<ul><li><pre>");
                    int count = 0;
                    while((line = in.readLine())!=null && !line.startsWith(" concurrent-mark-sweep perm gen total")) {
                        if(count<2) pw.println(line);
                        count++;
                        if(line.trim().length()==0) {
                            count = 0;
                            pw.write("</pre></li><li><pre>");
                        }
                    }
                    pw.println(line); pw.println("</pre></li></ul>");
                    pw.println("</pre></body></html>");
                    pw.flush(); pw.close();
                    threadDumpNum++;
                }
                if(line==null) break;

                // ignored lines
                if(line.trim().startsWith("|") || line.startsWith("Killed by signal")
                        || line.trim().length()==0
                        || "[ERROR] |#]".equals(line) || "[WARNING] |#]".equals(line))
                    continue;

                // lines without dates
                if(! (line.startsWith("[GC ["))) try {
                    Matcher m = pattern.matcher(line);
                    if(m.matches()) {
                        ttl.add(appservLogDf.parse(m.group(1)), lineNum);
                    }
                } catch (Exception e) {
                    System.err.println("Date mismatch in \"" + line + "\".");
                    e.printStackTrace();
                }
                out.print("<span id='");
                out.print(integers.format(lineNum));
                out.print("'>");
                out.print(line);
                out.println("</span>");
                lineNum++;
            }

            // footer
            out.print(pageTemplateBits[2]);
            out.print("Created on ");
            out.println(new Date());

            out.print(pageTemplateBits[4]);
            Util.outputJsonHash(out, "Curriki.monitor.timeToAnchor", ttl.buckets);
            out.print(pageTemplateBits[6]);

            out.flush();
            out.close();
            in.close();
        }
    }

    // ==============================================================================
    private class MySQLProcessListSplitter {
        PrintWriter out = null;
        int num = 0;
        String[] pageTemplateBits;

        private void renewOut() throws IOException {
            if(out!=null) {
                out.print(pageTemplateBits[2]);
                out.print(new Date());
                out.print(pageTemplateBits[4]);
                out.flush(); out.close();
            }
            String fileName = "output/mysqlProcesses_"+num+".html";
            out = Util.makePrintWriter(fileName);
            out.println(pageTemplateBits[0]);
        }
        private void parse() throws Exception {
            pageTemplateBits = IOUtils.toString(this.getClass().getResourceAsStream("monitorResources/mysqlProcesses_x_Template.html"), "utf-8")
                    .split("«|»");
            if(! ("log".equals(pageTemplateBits[1]) && "date".equals(pageTemplateBits[3]) )) {
                throw new IllegalStateException("Template is malformed, expected sequence log, date.");
            }

            LineNumberReader in = new LineNumberReader(new InputStreamReader(new FileInputStream("mysqlProcessList.txt"),"utf-8"));
            String line;
            renewOut();
            while( (line = in.readLine())!=null ) {
                if("----------------------".equals(line)) {
                    num++;
                    renewOut();
                    continue;
                }
                out.println(line);
            }

            out.flush();
            out.close();
            in.close();
        }

    }




    // =======================================================================================================
    private class TimeToLine {

        private TimeToLine(float granularity, long start, long end) {
            this.start = start;
            this.end = end;
            this.granularity = granularity;
            this.numBuckets = (int) ((end-start)/granularity + 0.5f);
            this.buckets = new int[numBuckets];
            for(int i=0; i<numBuckets; i++) buckets[i] = -1;
        }

        float granularity;
        long start, end;
        int numBuckets;
        int[] buckets;

        void add(Date date, int lineNum) {
            int bucketNum = convert(date);
            if(bucketNum<0 || bucketNum>=buckets.length) {
                System.err.println("Ignoring date " + date + " being out of range (" + bucketNum + " of " + numBuckets + ").");
            } else {
                int existing = buckets[bucketNum];
                if(existing==-1 || existing > lineNum)
                    buckets[bucketNum] = lineNum;
            }
        }

        private int convert(Date date) {
            return (int) ((date.getTime()-start)/granularity);
        }
    }
}
