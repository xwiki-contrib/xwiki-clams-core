package org.curriki.tools.loadtest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.jdom2.*;
import org.jdom2.filter.ContentFilter;
import org.jdom2.filter.ElementFilter;
import org.jdom2.filter.Filter;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;

/**
 */
class Checker {

    private static Log LOG = LogFactory.getLog(Checker.class);
    static Namespace HTMLNS = Namespace.getNamespace("http://www.w3.org/1999/xhtml");

    static Document parseResponse(HttpResponse x) throws IOException, JDOMException {

        String content = org.apache.commons.io.IOUtils.toString(x.getEntity().getContent(), "utf-8");
        content = content.replaceAll("curriki:", "").replaceAll("Curriki:","");
        //System.out.println("Content is " + content);

        SAXBuilder builder = new SAXBuilder("org.cyberneko.html.parsers.SAXParser");
        builder.setProperty("http://cyberneko.org/html/properties/default-encoding","UTF-8");
        return builder.build(new StringReader(content));
    }

    static void checkDocumentHas(String elementName, String id, Document doc, String mustFind) {
        Element found = null;
        Filter filter = new ElementFilter(elementName, HTMLNS);
        Iterator<Content> it = doc.getRootElement().getDescendants(filter);
        while(it.hasNext()) {
            Element elt = (Element) it.next();
            if(id==null || id.equals(elt.getAttributeValue("id"))) {
                found = elt; break;
            }
        }

        // compute descendant texts
        StringBuilder builder = new StringBuilder();
        it = found.getDescendants(new ContentFilter(ContentFilter.TEXT));
        while(it.hasNext()) {
            Text t = (Text) it.next();
            builder.append(t.getTextNormalize());
        }
        String value = builder.toString();
        if(value.length()==0l)
            throw new IllegalStateException("No element following pattern \"" + elementName + "\" with id \""+id+"\" has been found.");
        else if(mustFind.equals(value)) {
            // nothing to do
            LOG.info("----  Expected text is found.");
        } else {
            throw new IllegalStateException("Element's text differs: \n  Expected: \"" + mustFind + "\"\n  But found: \"" + value + "\"." );
        }
    }

    static void assertResponseIsRedirect(HttpResponse x, String location) {
        if(x.getStatusLine().getStatusCode() == 200) {
            LOG.warn("Received 200: ");
            try {
                x.getEntity().writeTo(System.out);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        if(x.getStatusLine().getStatusCode()!=302) throw new IllegalStateException("Did not receive a redirect! (Status line : " + x.getStatusLine() + "");
        Header[] headers = x.getHeaders("Location");
        if(headers.length!=1) throw new IllegalStateException("Was expecting one Location header!");
        if(location.equals(headers[0].getValue())) {
            // ok
        } else {
            throw new IllegalStateException("Different location target: received \"" + headers[0].getValue() + "\" but expected \"" + location + "\".");
        }
    }
}
