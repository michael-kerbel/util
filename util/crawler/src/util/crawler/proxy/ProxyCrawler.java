package util.crawler.proxy;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.Logger;

import util.crawler.CrawlParams;
import util.crawler.Crawler;
import util.http.HttpClientFactory;


public class ProxyCrawler {

   private static Logger      _log             = Logger.getLogger(ProxyCrawler.class);

   private static CrawlParams SAMAIR_PARAMS    = new CrawlParamsWithXsltFromClasspath("proxies-samair.xsl");
   static {
      SAMAIR_PARAMS.setDontFollowRegexes(Collections.EMPTY_LIST);
      SAMAIR_PARAMS.setFollowRegexes(Arrays.asList("/proxy/proxy-\\d+\\.htm"));
      SAMAIR_PARAMS.setHost("www.samair.ru");
      SAMAIR_PARAMS.setId("proxies");
      SAMAIR_PARAMS.setNumberOfThreads(1);
      SAMAIR_PARAMS.setStartURLs(Arrays.asList("/proxy/proxy-100.htm", "/proxy/proxy-200.htm", "/proxy/proxy-300.htm", "/proxy/proxy-01.htm"));
   }

   private static CrawlParams HIDEMYASS_PARAMS = new CrawlParamsWithXsltFromClasspath("proxies-hidemyass.xsl");
   static {
      HIDEMYASS_PARAMS.setDontFollowRegexes(Collections.EMPTY_LIST);
      HIDEMYASS_PARAMS.setFollowRegexes(Arrays.asList("/proxy-list/\\d+"));
      HIDEMYASS_PARAMS.setHost("hidemyass.com");
      HIDEMYASS_PARAMS.setId("proxies");
      HIDEMYASS_PARAMS.setNumberOfThreads(1);
      HIDEMYASS_PARAMS.setUseCookies(true);
      HIDEMYASS_PARAMS.setUserAgent("Mozilla/5.0 (Windows NT 5.1; rv:15.0) Gecko/20100101 Firefox/15.0");
      HIDEMYASS_PARAMS.setStartURLs(Arrays.asList("http://hidemyass.com/proxy-list/"));
   }


   public static void main( String[] args ) {
      refreshProxyList(null, null, null, 120, 3000);
   }

   public static void refreshProxyList( String testHost, List<Pattern> sanePatterns, List<Pattern> insanePatterns, int maxTimeToMeasureLatency,
         long maxResponseTimeInMillis ) {
      ProxyCrawler proxyCrawler = new ProxyCrawler();
      proxyCrawler.crawl();

      HttpHost latencyTestHost = testHost != null ? new HttpHost(testHost) : null;
      ProxyPool proxyPool = new ProxyPool(proxyCrawler._proxyList, latencyTestHost, sanePatterns, insanePatterns, null, null, null);
      ProxyList fastProxies = proxyPool.measureLatency(maxTimeToMeasureLatency, maxResponseTimeInMillis);

      ProxyList.storeProxyList(fastProxies);
   }


   Set<String> _proxyAdresses = new HashSet<String>();

   ProxyList   _proxyList     = new ProxyList();


   public void crawl() {
      new TheRealProxyCrawler(HIDEMYASS_PARAMS).crawl();
      //new TheRealProxyCrawler(SAMAIR_PARAMS).crawl();

      addProxiesFromProxyNova("http://www.proxynova.com/proxy_list.txt");
      addProxiesFromProxyNova("http://www.proxynova.com/proxy_list.txt?country=us,de,fr,es,ch,it,pl,uk,ca,no");

      int n = _proxyList.getProxies().size();
      if ( n == 0 ) {
         _log.warn("initializing from static file");
         _proxyList.initFromDisk();
         n = _proxyList.getProxies().size();
         _log.info("got " + n + " proxies");
      }
   }

   public ProxyList getProxyList() {
      return _proxyList;
   }

   private void addProxiesFromProxyNova( String url ) {
      try {
         HttpClient httpClient = new HttpClientFactory().create();
         HttpGet get = HttpClientFactory.createGet(url);
         HttpResponse response = httpClient.execute(get);
         String page = HttpClientFactory.readPage(response);
         for ( String line : StringUtils.split(page, "\n") ) {
            if ( line.trim().isEmpty() ) {
               continue;
            }
            if ( Character.isDigit(line.charAt(0)) ) {
               _proxyList.addProxy(line);
            }
         }
      }
      catch ( Exception argh ) {
         _log.warn("Failed to get proxies from proxynova.", argh);
      }
   }


   private static final class CrawlParamsWithXsltFromClasspath extends CrawlParams {

      private String _filename;


      public CrawlParamsWithXsltFromClasspath( String filename ) {
         _filename = filename;
      }

      @Override
      public String getXslContents() {
         try {
            return IOUtils.toString(ProxyCrawler.class.getResourceAsStream(_filename));
         }
         catch ( Exception argh ) {
            throw new RuntimeException("Failed to read proxy xsl file.", argh);
         }
      }
   }

   private class TheRealProxyCrawler extends Crawler {

      public TheRealProxyCrawler( CrawlParams params ) {
         super(params);
      }

      @Override
      public void crawl() {
         _log.info("starting crawl for proxies using " + getParams().getHost());

         super.crawl();

         for ( String a : _proxyAdresses ) {
            _proxyList.addProxy(a);
         }
         int n = _proxyList.getProxies().size();
         _log.info("got " + n + " proxies");
      }

      @Override
      protected synchronized int addResult( Map<String, String>[] maps ) {
         int added = 0;
         for ( Map<String, String> map : maps ) {
            String ip = map.get("ip");
            String port = map.get("port");
            if ( ip != null && !ip.isEmpty() && StringUtils.trimToNull(port) != null ) {
               if ( _proxyAdresses.add(ip + ":" + port) ) {
                  added++;
               }
            }
         }
         return added;
      }
   }
}
