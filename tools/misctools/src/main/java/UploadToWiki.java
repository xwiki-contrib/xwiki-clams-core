import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.net.ssl.*;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.lang.String;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class UploadToWiki {

    public static void main(String[] args) throws Exception {
        new UploadToWiki(new File(args[0]), args[1], args[2], args[3]);
    }

    public UploadToWiki(File file, String comment, String fieldName, String uploadTo) throws Exception  {
        // gather params
        this.file = file;
        if(file==null) throw new IllegalArgumentException("file is missing");
        this.space = file.getParentFile().getName();
        this.name = file.getName().replaceFirst("\\.[a-zA-Z0-9]+$","");
        if(name.matches(".+_[a-z][a-z]")) {
            this.language = name.substring(name.length()-2);
            this.name = name.substring(0, name.length()-2);
        }


        // read uploadTo
        if(comment!=null) this.comment = comment;
        if(fieldName!=null) this.fieldName = fieldName;
        this.uploadTo = uploadTo;
        if(uploadTo==null) throw new IllegalArgumentException("uploadTo is missing");
        System.out.println("Uploading " + file + " to " + uploadTo);

        readAuth();
        String userPassToken = new sun.misc.BASE64Encoder().encode((userName + ":" + password).getBytes());

        readFileContents();

        // read modification date
        pullResource(userPassToken, false);


        // post to /xwiki/bin/save/$spacename/$pagename?language=$language
        URL uploadURL = new URL(uploadTo + "/xwiki/bin/saveandcontinue/" + space + "/" + name + "?language=" + language);
        HttpURLConnection conn = (HttpURLConnection) uploadURL.openConnection();
        if(conn instanceof HttpsURLConnection) {
            SSLSocketFactory factory = disableSSLValidation();
            ((HttpsURLConnection) conn).setSSLSocketFactory(factory);
        }
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Authorization", "Basic " + userPassToken);
        conn.setInstanceFollowRedirects(false);
        Writer out = new OutputStreamWriter(conn.getOutputStream(), "utf-8");
        out.write(fieldName + "=" + URLEncoder.encode(fileContents, "utf-8"));
        out.flush();
        out.close();

        int responseCode = conn.getResponseCode();
        String allOk = "";
        if(responseCode == 302) {
            URL target = new URL(uploadURL, conn.getHeaderField("Location"));
            if(target.getPath().equals("/xwiki/bin/edit/" + space + "/" + name)) allOk = " - to correct position.";
            else allOk = " - redirecting to " + target + " !!! ";
        }
        System.out.println("-- Uploading: " + conn.getResponseCode() + " " + conn.getResponseMessage() + allOk);


        // pull the XML from /xwiki/bin/view/$spacename/$pagename?xpage=xml&language=$language verify
        // post to /xwiki/bin/save/$spacename/$pagename?language=$language
        pullResource(userPassToken, true);
     }

    private void pullResource(String encoding, boolean verify) throws Exception {
        HttpURLConnection conn;URL readURL = new URL(uploadTo + "/xwiki/bin/view/" + space + "/" + name + "?xpage=xml&language=" + language);
        conn = (HttpURLConnection) readURL.openConnection();
        if(conn instanceof HttpsURLConnection) {
            SSLSocketFactory factory = disableSSLValidation();
            ((HttpsURLConnection) conn).setSSLSocketFactory(factory);
        }
        conn.setUseCaches(false);
        //String encoding = new sun.misc.BASE64Encoder().encode((userName + ":" + password).getBytes());
        conn.setRequestProperty("Authorization", "Basic " + encoding);
        System.out.println("-- Downloading: " + conn.getResponseCode() + " " + conn.getResponseMessage());

        final StringBuilder contentB = new StringBuilder(), updateDate = new StringBuilder(), version = new StringBuilder();
        SAXParserFactory.newInstance().newSAXParser().parse(conn.getInputStream(), new DefaultHandler() {
            boolean inContent = false, incontentUpdateDate=false, inVersion = false;
            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                if(UploadToWiki.this.fieldName.equals(qName)) inContent = true;
                if("contentUpdateDate".equals(qName)) incontentUpdateDate = true;
                if("version".equals(qName)) inVersion= true;
            }

            public void endElement(String uri, String localName, String qName) throws SAXException {
                if(UploadToWiki.this.fieldName.equals(qName)) inContent = false;
                if("contentUpdateDate".equals(qName)) incontentUpdateDate = false;
                if("version".equals(qName)) inVersion= false;
            }

            public void characters(char[] ch, int start, int length) throws SAXException {
                if(inContent) contentB.append(ch, start, length);
                if(incontentUpdateDate) updateDate.append(ch, start, length);
                if(inVersion) version.append(ch, start, length);
            }

        });

        String date = "-";
        if(updateDate!=null && updateDate.length()>0) date= new Date(Long.parseLong(updateDate.toString())).toString();
        System.out.println("-- Version " + version + ", modification date: " + date);

        if(verify) {
            boolean verified = contentB.toString().equals(fileContents);
            System.out.println("-- Content equals? " + verified);
            if(!verified) {
                File f= new File("/tmp/upload-failed-read-from-server");
                Writer o = new OutputStreamWriter(new FileOutputStream(f),"utf-8");
                o.write(contentB.toString());
                o.flush(); o.close();
                System.out.println("-- Please compare \n   " + f + " with \n   " + file);
                System.exit(1);
            }
            int p=0;
            // while((p=conn.getInputStream().read())!=-1) System.out.print((char) p);
        }
    }

    private File file;
    private String space, name, language = "en";
    private String comment = "cli upload";
    private String fieldName = "content";
    private String uploadTo;
    private String userName, password;
    private String fileContents;

    private void readAuth() throws Exception {
        LineNumberReader r = new LineNumberReader(new InputStreamReader(new FileInputStream(
                new File(new File(System.getProperty("user.home")), ".upload-auth")), "utf-8"));
        String line = "";
        String host = uploadTo.replaceAll("https?://","");
        Pattern p = Pattern.compile("[whitespace]*"+ host + "[whitespace]*([^:]+):(.*)");
        Matcher m = null;
        while((line=r.readLine())!=null) {
            m = p.matcher(line);
            if(m.matches()) {
                break;
            } else line = null;
        }
        if(line!=null) {
            userName = m.group(1);
            password = m.group(2);
        } else {
            throw new IllegalArgumentException("Auth not found for host " + host + ".");
        }
    }

    /* this seems to fail currently */
    private SSLSocketFactory disableSSLValidation() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        } };

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create an ssl socket factory with our all-trusting manager
        return sc.getSocketFactory();
    }

    private void readFileContents() throws Exception {
        StringBuilder b = new StringBuilder((int) (file.length()*1.1f));
        InputStreamReader r = new InputStreamReader(new FileInputStream(file), "utf-8");
        char[] buff = new char[128];
        int l=0;
        while((l=r.read(buff,0,128))>0) {
            b.append(buff,0,l);
        }
        fileContents = b.toString();
    }

}
