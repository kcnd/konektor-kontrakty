/*
 * Jakekoli sireni tohoto dila nebo jeho casti neni dovoleno.
 * Pouziti bez souhlasu autora je trestne podle zakona.
 */
package konektor;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 *
 * @author ivanjelinek
 */
class Connector {

    private String ipES;
    private String portES;
    private String configURL = "config.txt";
    private ArrayList<String> allUrls = new ArrayList();
    private HashSet<String> usedUrls = new HashSet();
    //  private String urlRegex = "idnes";
    private String urlRegex = "(https|http)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
    private ArrayList<String> startsWith = new ArrayList();
    private String plainTextHTML;
    private String indexES = "webdefaultindex";
    private String typeES = "webdefaulttype";
    private String thisUrl;

    public Connector() {
        loadReportConfig();
        init();
        System.out.println("TOTAL " + this.allUrls.size());
    }

    /**
     * metoda nacte konfig
     *
     * @throws FileNotFoundException nemam konfig
     * @throws IOException chyba v pristupu k souboru
     */
    private void loadReportConfig() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(this.configURL));
            String line = br.readLine();
            String[] pole;

            while (line != null) {
                if (line.toLowerCase().contains("[license]")) {
                    while (!line.toLowerCase().contains("[server]")) {
                        line = br.readLine();
                        pole = line.split(":");
                        if (pole[0].equals("licensekey")) {
                            //licensekey:veovber
                            License.checkLicense(pole[1]);
                        }
                    }
                }
                if (line.toLowerCase().contains("[server]")) {
                    while (!line.toLowerCase().contains("[task]")) {
                        line = br.readLine();
                        pole = line.split(":");
                        if (pole[0].equals("ip")) {
                            //ip:192.168.0.0
                            this.ipES = pole[1];
                        }
                        if (pole[0].equals("port")) {
                            this.portES = pole[1];
                        }
                        if (pole[0].equals("index")) {
                            this.indexES = pole[1];
                        }
                        if (pole[0].equals("type")) {
                            this.typeES = pole[1];
                        }
                        if (pole[0].equals("contains")) {
                            this.startsWith.add(pole[1]);
                        }
                    }
                }
                if (line.toLowerCase().contains("[task]")) {
                    while (line != null) {
                        pole = line.split("@");
                        if (pole[0].equals("url")) {
                            //url@www.seznam.cz
                            System.out.println("Starting Web Downloader for page: " + pole[1]);
                            download(pole[1]);
                        }
                        line = br.readLine();
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println(new Date() + " IO Exception in reading config.");
        }
    }

    private void download(String urlPage) {
        try {
            thisUrl = urlPage;
            URL url = new URL(urlPage);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("GET");

            Metadata metadata = new Metadata();
            LinkContentHandler linkHandler = new LinkContentHandler();
            ContentHandler textHandler = new BodyContentHandler();
            ToHTMLContentHandler toHTMLHandler = new ToHTMLContentHandler();
            TeeContentHandler teeHandler = new TeeContentHandler(linkHandler, textHandler, toHTMLHandler);
            ParseContext parseContext = new ParseContext();
            HtmlParser parser = new HtmlParser();
            parser.parse(httpCon.getInputStream(), teeHandler, metadata, parseContext);
            plainTextHTML = textHandler.toString();

            checkUrls(linkHandler.getLinks());
            ingestPage(urlPage, plainTextHTML);
            System.gc();

        } catch (MalformedURLException ex) {
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ProtocolException ex) {
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
            if(!allUrls.isEmpty()){
                usedUrls.add(allUrls.remove(0));
            }
            //init();
        } catch (SAXException ex) {
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
        } catch (TikaException ex) {
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            //Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
            usedUrls.add(allUrls.remove(0));
            //init();
        }
    }

    private void checkUrls(List<Link> seznam) {
        for (Link link : seznam) {
            String url = link.getUri();
            boolean toIndex = true;
            if (!url.startsWith("http")) {
                url = thisUrl + url;
            }
            if (url.startsWith("http://")) {
                for (String restriction : startsWith) {
                    if (url.contains(restriction)) {
                        for (String used : allUrls) {
                            if (url.contains(used)) {
                                toIndex = false;
                                break;
                            }
                        }
                        break;
                    } else {
                        toIndex = false;
                    }
                }
            } else {
                toIndex = false;
            }
            if (toIndex) {
                allUrls.add(url);
            }
        }
    }

    private void ingestPage(String urlPage, String bodyPage) {

        String message = "{ \"body\" : \"" + bodyPage.replaceAll("\n", " ").replaceAll("\t", " ").replaceAll("  ", "") + "\", \"url\" : \""
                + urlPage + "\", \"date\" : \"" + new Date() + "\"}";
        //System.out.println(message);
        try {
            URL url = new URL("http://" + this.ipES + ":" + this.portES + "/" + this.indexES + "/" + this.typeES + "/");
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("POST");
            OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
            out.write(message);
            out.close();
            BufferedReader in = new BufferedReader(new InputStreamReader(httpCon.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            //System.out.println(response);
            in.close();
        } catch (MalformedURLException ex) {
            Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
            //init();
        } catch (IOException ex) {
            //Logger.getLogger(Connector.class.getName()).log(Level.SEVERE, null, ex);
            usedUrls.add(allUrls.remove(0));
            //init();
        } catch (Exception e) {
            usedUrls.add(allUrls.remove(0));
        }
    }

    private void init() {
        while (allUrls.size() > 0) {
            System.out.println("URLs to check " + this.allUrls.size());
            System.out.println("URLs checked  " + this.usedUrls.size());
            for (String used : usedUrls) {
                if (allUrls.isEmpty()) {
                    break;
                }
                if (used.equals(allUrls.get(0))) {
                    allUrls.remove(0);
                }
            }
            if (allUrls.isEmpty()) {
                break;
            }
            System.out.println("Downloading " + allUrls.get(0));
            usedUrls.add(allUrls.get(0));
            download(allUrls.remove(0));
        }

    }
}
