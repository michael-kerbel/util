package util.crawler.proxy;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.http.HttpHost;
import org.apache.log4j.Logger;

import util.crawler.proxy.ProxyList.ProxyAddress;
import util.string.StringTable;
import util.string.StringTable.Alignment;


public class ProxyPool {

   private static Logger        _log  = Logger.getLogger(ProxyPool.class);

   private ProxyList            _proxyList;
   private List<Proxy>          _allProxies;
   private BlockingQueue<Proxy> _proxies;
   private AtomicInteger        _size = new AtomicInteger();


   public ProxyPool( ProxyList proxyList ) {
      init(proxyList, null, null, null, null, null, null);
   }

   public ProxyPool( ProxyList proxyList, HttpHost latencyTestHost, List<Pattern> sanePatterns, List<Pattern> insanePatterns, String userAgent,
         String authenticationUser, String authenticationPassword ) {
      init(proxyList, latencyTestHost, sanePatterns, insanePatterns, userAgent, authenticationUser, authenticationPassword);
   }

   public Proxy checkoutProxy() {
      try {
         Proxy proxy;
         proxy = _proxies.poll(1, TimeUnit.HOURS);
         int size = _size.decrementAndGet();
         if ( size <= 0 ) {
            _log.warn("proxy pool emptied during checkoutProxy, because all proxies failed latency check.");
         }
         while ( proxy != null && proxy.getStats().getLastByteLatency() < 0 ) {
            proxy = _proxies.poll(1, TimeUnit.HOURS);
            size = _size.decrementAndGet();
            if ( size <= 0 ) {
               _log.warn("proxy pool emptied during checkoutProxy, because all proxies failed latency check.");
            }
         }
         if ( proxy == null ) {
            return checkoutProxy();
         }

         if ( size() < 3 ) {
            _log.warn("only " + size() + " proxies in proxy pool left");
         }

         return proxy;
      }
      catch ( InterruptedException argh ) {
         _log.info("checkout from proxy pool interrupted", argh);
         return null;
      }
   }

   /**
    * @param maxTimeToMeasureLatency in s
    * @return proxies which responded faster than <code>maxResponseTimeInMillis</code>
    */
   public ProxyList measureLatency( int maxTimeToMeasureLatency, final long maxResponseTimeInMillis ) {
      final ProxyList fastProxies = new ProxyList();

      ThreadPoolExecutor executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(50);

      for ( final Proxy p : _proxies ) {
         executor.execute(new Runnable() {

            @Override
            public void run() {
               long lastByteLatency = p.getStats().getLastByteLatency();
               if ( !p.isInsane() && lastByteLatency > 0 && lastByteLatency < maxResponseTimeInMillis ) {
                  fastProxies.addProxy(p.getAddress().toString());
               }
               _log.debug(p);
            }
         });
      }

      executor.shutdown();
      try {
         executor.awaitTermination(maxTimeToMeasureLatency, TimeUnit.SECONDS);
      }
      catch ( InterruptedException argh ) {
         // ignore
      }

      executor.shutdownNow();

      return fastProxies;
   }

   public void returnProxy( Proxy p ) {
      _proxies.add(p);
      _size.incrementAndGet();
   }

   public int size() {
      return _size.get();
   }

   @Override
   public String toString() {
      Collections.sort(_allProxies);
      StringTable table = new StringTable( //
         new StringTable.Col("ip", Alignment.Right, 21), new StringTable.Col("gets", Alignment.Right, 7), //
         new StringTable.Col("errors", Alignment.Right, 7), //
         new StringTable.Col("stillInUse", Alignment.Center, 10), //
         new StringTable.Col("avgRequestTime", Alignment.Right, 14), //
         new StringTable.Col("avgSuccessfulRequestTime", Alignment.Right, 24), //
         new StringTable.Col("avgFaultyRequestTime", Alignment.Right, 20), //
         new StringTable.Col("firstByteLatency", Alignment.Right, 16), //
         new StringTable.Col("lastByteLatency", Alignment.Right, 15), //
         new StringTable.Col("clientAccesses", Alignment.Right, 14), //
         new StringTable.Col("faultRatio", Alignment.Right, 10) //
      );
      for ( Proxy p : _allProxies ) {
         if ( p.getStats().getTotalGets() > 0 ) {
            table.addRow(p.getAddress()._ip + ":" + p.getAddress()._port, //
               "" + p.getStats().getTotalGets(), //
               "" + p.getStats().getFaultyGets(), //
               "" + _proxies.contains(p), //
               p.getStats().getAverageRequestTime() + " ms", //
               p.getStats().getAverageSuccessfulRequestTime() + " ms", //
               p.getStats().getAverageFaultyRequestTime() + " ms", //
               p.getStats().getFirstByteLatency() + " ms", //
               p.getStats().getLastByteLatency() + " ms", //
               "" + p.getStats().getClientAccesses(), //
               NumberFormat.getPercentInstance().format(p.getStats().getFaultRatio()) //
            );
         }
      }
      return table.toString();
   }

   protected void init( ProxyList proxyList, HttpHost latencyTestHost, List<Pattern> sanePatterns, List<Pattern> insanePatterns, String userAgent,
         String authenticationUser, String authenticationPassword ) {
      _proxyList = proxyList;
      _allProxies = new ArrayList<Proxy>();
      _proxies = new PriorityBlockingQueue<Proxy>();
      for ( ProxyAddress a : _proxyList.getProxies() ) {
         Proxy proxy = new Proxy(a);
         proxy.setLatencyTestHost(latencyTestHost);
         proxy.setSanePatterns(sanePatterns);
         proxy.setInsanePatterns(insanePatterns);
         proxy.setUserAgent(userAgent);
         proxy.setAuthenticationUser(authenticationUser);
         proxy.setAuthenticationPassword(authenticationPassword);
         _proxies.add(proxy);
         _allProxies.add(proxy);
      }
      _size.set(_proxies.size());
   }
}
