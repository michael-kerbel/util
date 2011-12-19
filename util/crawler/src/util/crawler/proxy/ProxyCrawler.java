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
import org.apache.log4j.Logger;

import util.crawler.CrawlParams;
import util.crawler.Crawler;


public class ProxyCrawler extends Crawler {

   private static Logger      _log   = Logger.getLogger(ProxyCrawler.class);
   private static CrawlParams PARAMS = new CrawlParams() {

                                        @Override
                                        public String getXslContents() {
                                           try {
                                              return IOUtils.toString(ProxyCrawler.class.getResourceAsStream("proxies.xsl"));
                                           }
                                           catch ( Exception argh ) {
                                              throw new RuntimeException("Failed to read proxy xsl file.", argh);
                                           }
                                        };
                                     };
   static {
      PARAMS.setDontFollowRegexes(Collections.EMPTY_LIST);
      PARAMS.setFollowRegexes(Arrays.asList("/proxy/proxy-..\\.htm"));
      PARAMS.setHost("www.samair.ru");
      PARAMS.setId("proxies");
      PARAMS.setNumberOfThreads(1);
      PARAMS.setStartURLs(Arrays.asList("/proxy/proxy-01.htm"));
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


   public ProxyCrawler() {
      super(PARAMS);
   }

   @Override
   public void crawl() {
      _log.info("starting crawl for proxies");

      super.crawl();

      for ( String a : _proxyAdresses ) {
         _proxyList.addProxy(a);
      }
      int n = _proxyList.getProxies().size();
      _log.info("got " + n + " proxies");
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
