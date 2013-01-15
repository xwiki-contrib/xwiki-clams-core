package org.curriki.tools.loadtest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.BasicClientCookie2;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 */
public class XWikiHttpClient extends DefaultHttpClient {

    private static Log LOG = LogFactory.getLog(XWikiHttpClient.class);

    public XWikiHttpClient(String url, String userName, String userPassword) throws IOException {
        super(new ThreadSafeClientConnManager());
        this.url = new URL(url);
        this.userName = userName; this.userPassword = userPassword;

        this.host = new HttpHost(this.url.getHost(), this.url.getPort(), this.url.getProtocol());
        this.localcontext = new BasicHttpContext();
        ((ThreadSafeClientConnManager) super.getConnectionManager()).setDefaultMaxPerRoute(100);
        super.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.BROWSER_COMPATIBILITY);
        super.setCookieStore(new BasicCookieStore());
        super.getParams().setParameter(ClientPNames.HANDLE_REDIRECTS, Boolean.FALSE);
    }

    final URL url;
    final String userName, userPassword;
    final HttpHost host;
    final HttpContext localcontext;

    public HttpResponse executeWithRelogin(HttpRequest request) throws HttpException, IOException {
        HttpResponse response = super.execute(host, request, localcontext);
        int sc = response.getStatusLine().getStatusCode();
        Header locationHeader = response.getFirstHeader("Location");
        String loc = null; if(locationHeader!=null) loc = locationHeader.getValue();
        if((sc==301 || sc ==302 || sc==303) && loc!=null && loc.contains("Login")) {
            tryLogin();
            response = super.execute(host, request, localcontext);
        }
        return response;
    }

    public HttpResponse tryLogin() throws IOException {
        StatusLine status = null;
        HttpResponse response = null;
        try {
            LOG.debug("Logging in.");
            HttpPost loginPost = new HttpPost(new URL(url,"/xwiki/bin/loginsubmit/XWiki/XWikiLogin?xredirect=%2Fxwiki%2Fbin%2Fview%2FSearch2%2FUserName%3Fxpage%3Dplain&xpage=plain").toExternalForm());
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            formparams.add(new BasicNameValuePair("j_username", "solr"));
            formparams.add(new BasicNameValuePair("j_password",  "currikiSearchFast"));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
            loginPost.setEntity(entity);
            response = super.execute(host, loginPost, localcontext);
            status = response.getStatusLine();
            if(status.getStatusCode()/100==3) { // consume the redirect
                response = super.execute(host, new HttpGet(response.getHeaders("Location")[0].getValue()),localcontext);
                status = response.getStatusLine();
            }
        } catch (IOException e) {
            LOG.warn(e);
            throw new IllegalStateException("Can't loging. Giving up.");
        }
        if(status.getStatusCode() / 100 == 3 || status.getStatusCode() / 100 == 2) {
            verifyLoggedInUserName();
            LOG.debug("Successful Login.");
        } else {
            LOG.info("Failed login: " + status);
            throw new IllegalStateException("Can't login to XWiki with user \"" + userName + "\".");
        }
        return response;
    }

    public String verifyLoggedInUserName() throws IOException {
        String u= new URL(url,"/xwiki/bin/view/Search2/UserName?xpage=plain").toExternalForm();
        HttpResponse response = super.execute(host, new HttpGet(u), localcontext);
        StatusLine status = response.getStatusLine();
        if(status.getStatusCode()!=200) return status.toString();
        String userName = responseAscii(response);
        LOG.info("--- Obtained userName \"" + userName + "\".");

        if("XWiki.XWikiGuest".equals(userName.trim())) { // login and redo it
            tryLogin();
            response = super.execute(host, new HttpGet(u), localcontext);
            status = response.getStatusLine();
            userName = responseAscii(response);
        }
        if(status.getStatusCode()!=200) return status.toString();
        long l = response.getEntity().getContentLength();
        if(userName!=null && userName.length()==0) {
            return "<empty>" + l;
        }
        else return userName;
    }

    public static String responseAscii(HttpResponse response) throws IOException {
        StringBuilder buff = new StringBuilder(20);
        InputStream in = response.getEntity().getContent();
        int r=0;
        while((r=in.read())!=-1) buff.append((char) r);
        return buff.toString();
    }

    private Cookie getJSessionIdCookie(boolean remove) {
        Iterator<Cookie> itC = super.getCookieStore().getCookies().iterator();
        while(itC.hasNext()) {
            Cookie cookie = itC.next();
            if("JSESSIONID".equals(cookie.getName())) {
                if(remove) itC.remove();
                return cookie;
            }
        }
        return null;
    }

    public String getClusterNodeOfSession() {
        String value = getJSessionIdCookie(false).getValue();
        if(!value.contains(".")) throw new IllegalStateException(
                "JSESSIONID cookie does not contain a period (it is \"" + value + "\").");
        value = value.substring(value.indexOf('.')+1);
        return value;
    }

    public String changeClusterNode(String node1, String node2) {
        node1 = "." + node1; node2 = "." + node2;
        String returnValue = null;
        Cookie cookie = getJSessionIdCookie(true);
        String value = cookie.getValue();
        LOG.info("---- cookie is " + value);
        int p = value.indexOf('.');
        if(value.endsWith(node1)) {
            LOG.info("-- Changing cookie to " + node2 + ".");
            returnValue = node2;
            BasicClientCookie2 stdCookie = new BasicClientCookie2("JSESSIONID", value.substring(0, p)+node2);
            stdCookie.setVersion(1);
            stdCookie.setDomain(url.getPath());
            stdCookie.setPorts(new int[] {url.getPort()});
            stdCookie.setPath("/");
            stdCookie.setSecure(true);
            super.getCookieStore().addCookie(stdCookie);
        } else if (value.endsWith(node2)) {
            LOG.info("---- No need to change cookie.");
            /* LOG.info("-- Changing cookie to " + node1 + ".");
            returnValue = node1;
            BasicClientCookie2 stdCookie = new BasicClientCookie2("JSESSIONID", value.substring(0, p)+node1);
            stdCookie.setVersion(1);
            stdCookie.setDomain(url.getPath());
            stdCookie.setPorts(new int[] {url.getPort()});
            stdCookie.setPath("/");
            stdCookie.setSecure(true);
            super.getCookieStore().addCookie(stdCookie); */
        } else throw new IllegalStateException("Cookie JSESSIONID=\"" + value + "\" is not one of the node-routes.");
        return returnValue;
    }


    public HttpResponse postForm(String url, String... keyVals) throws HttpException, IOException {
        HttpPost post = new HttpPost(url);
        List<NameValuePair> params = new LinkedList<NameValuePair>();
        for(String kv: keyVals) {
            int p = kv.indexOf(':');
            params.add(new BasicNameValuePair(kv.substring(0,p), kv.substring(p+1)));
        }
        post.setEntity(new UrlEncodedFormEntity(params));
        return this.executeWithRelogin(post);
    }

    public HttpResponse getPage(String url) throws IOException {
        return super.execute(new HttpGet(url));
    }
}
