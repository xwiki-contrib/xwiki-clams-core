import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.lang.*;
import java.lang.Exception;
import java.lang.Process;
import java.lang.String;
import java.lang.System;
import java.lang.Thread;
import java.util.LinkedList;
import java.util.List;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;

public class iContactMassiveUpdater {
    
    public static void main(String[] args) throws Exception {
        if(args.length<2) {
            System.err.println("Usage: iContactMassiveUpdater file uploadURL [line-to-start]");
        }
        int start = 0;
        if(args.length>2) start = Integer.parseInt(args[2]);
        LineReaderWithEnd in = new LineReaderWithEnd(new InputStreamReader(new FileInputStream(args[0])));
        int numDone = 0;
        String line;
        final List<String> list = new LinkedList<String>();
        long phaseStarted = System.currentTimeMillis();
        URL url = new URL(args[1]);
        boolean dryRun = args.length>3 && "dry".equals(args[3]);

        if(start>0) {
            System.out.println("Scrolling till "+ start + ".");
            while((line=in.readLine())!=null && numDone<start) {
                numDone++;
                //System.out.print(" - " + line);
                //if(numDone % 100==0) System.out.println();
            }
        }

        HttpClient httpClient = new HttpClient();
        String userInfo = url.getUserInfo();
        if(userInfo!=null && userInfo.indexOf(":")>0) {
            String user=null, pass=null;
            httpClient.getParams().setAuthenticationPreemptive(true);
            int p = userInfo.indexOf(":");
            user = userInfo.substring(0,p);
            pass = userInfo.substring(p+1);
            httpClient.getState().setCredentials(new AuthScope(url.getHost(), url.getPort(), AuthScope.ANY_REALM),
                    new UsernamePasswordCredentials(user, pass));
        }

        boolean doneOne = false;
        while( (line=in.readLine())!=null) {
            list.add(line);
            System.out.print(" -adding user-name: " + line);
            numDone++;
            if(numDone%100==0 || in.isFinished()) System.out.println();
            if(numDone % 1000==0 || in.isFinished()) {
                // sleep if necessary
                long waitTime = (phaseStarted+1000L*3*60)-System.currentTimeMillis();
                if(waitTime<0) waitTime = 10000;
                if(doneOne) {
                    System.out.println("-- Sleeping " + waitTime/1000 + " seconds.");
                    doneOne = true;
                    Thread.sleep(waitTime);
                }
                System.out.print("Posting till line " + numDone  + "... ");
                System.out.flush();
                long started = System.currentTimeMillis();
                
                PostMethod post = new PostMethod(url.toExternalForm());
                post.setRequestEntity(new RequestEntity() {
                    public boolean isRepeatable() {
                        return true;
                    }

                    public void writeRequest(OutputStream out) throws IOException {
                        Writer w = new OutputStreamWriter(out, "utf-8");
                        for (String l : list) {
                            w.write(l);
                            w.write("\n");
                        }
                        w.flush();
                    }

                    public long getContentLength() {
                        return -1;
                    }

                    public String getContentType() {
                        return "text/plain";
                    }
                });
                if(!dryRun) httpClient.executeMethod(post);
                System.out.println(" done: Posted till " + numDone + " (result " + post.getStatusLine() + " in "+ ((System.currentTimeMillis()-started)/1000f) +"s)");
                list.clear();
            }
        }
        System.out.println("Finished iContactMassiveUpdater.");
    }

    private static class LineReaderWithEnd extends LineNumberReader {
        public LineReaderWithEnd(Reader enclosed) throws IOException {
            super(enclosed);
            setAside = super.readLine();
        }
        private String setAside;

        private boolean isFinished() {
            return setAside == null;
        }

        public String readLine() throws IOException {
            String x = setAside;
            setAside = super.readLine();
            return x;
        }
    }
}