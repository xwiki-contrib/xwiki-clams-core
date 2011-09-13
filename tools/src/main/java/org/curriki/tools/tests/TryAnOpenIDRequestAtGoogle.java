package org.curriki.tools.tests;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;

public class TryAnOpenIDRequestAtGoogle {

    public static void main(String[] args) {
        Object o = null;
        String error = null;
        try {
            URL url = new URL("https://checkout.google.com/api/checkout/v2/reports/Merchant/MERCHANT_ID");
            o = url.getContent();
        } catch(Exception ex) {
            StringWriter w = new StringWriter();
            ex.printStackTrace(new PrintWriter(w));
            error = w.toString();
        }
        System.out.println("error  " + error);
        System.out.println("obtained " + o);
    }
}
