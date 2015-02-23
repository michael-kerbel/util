package util.crawler.proxy;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.crawler.proxy.ProxyList.ProxyAddress;
import util.http.HttpClientFactory;


public class Proxy implements Comparable<Proxy> {

   public static final HttpHost      DEFAULT_LATENCY_TEST_HOST = new HttpHost("www.chrono24.com");
   public static final List<Pattern> DEFAULT_SANE_PATTERNS     = Arrays.asList(new Pattern[] { Pattern.compile("chrono24"),//
                                                               });
   public static final List<Pattern> DEFAULT_INSANE_PATTERNS   = Arrays.asList(new Pattern[] { Pattern.compile("Page Not Responding"),//
         Pattern.compile("unimed"),//
         Pattern.compile("samair"),//
         Pattern.compile("404 Not Found"),//
         Pattern.compile("Hickory Orchards"),//
         Pattern.compile("<![CDATA[<body></body>]]>"),//
         Pattern.compile("Acceso denegado"),//
         Pattern.compile("CoDeeN CAPTCHA"),//
         Pattern.compile("Access denied"),//
         Pattern.compile("Barracuda Web Filter"),//
         Pattern.compile("Barracuda410"),//
         Pattern.compile("Now go away."),//
         Pattern.compile("www.caalbor.adv.br"),//
                                                               });


   public static void main( String[] args ) {
      for ( ProxyAddress address : new ProxyList().getProxies() ) {
         final Proxy proxy = new Proxy(address);
         proxy.measureLatency();
         System.err.println(proxy);
         //         new Thread() {
         //
         //            @Override
         //            public void run() {
         //
         //            }
         //         }.start();
      }
   }


   Logger _log = LoggerFactory.getLogger(getClass());

   private final ProxyAddress _address;
   private HttpHost           _proxyHost;

   private HttpClient         _httpClient;

   private Stats              _stats;

   private HttpHost           _latencyTestHost = DEFAULT_LATENCY_TEST_HOST;
   private List<Pattern>      _sanePatterns    = DEFAULT_SANE_PATTERNS;
   private List<Pattern>      _insanePatterns  = DEFAULT_INSANE_PATTERNS;

   private boolean            _insane;
   private String             _userAgent;

   private String             _authenticationUser;
   private String             _authenticationPassword;


   public Proxy( ProxyAddress address ) {
      _address = address;
      _proxyHost = new HttpHost(_address._ip, _address._port, "http");
      _stats = new Stats();
   }

   public void addFaultyGet( int time ) {
      _stats._sumFaultyRequestTimes += time;
      _stats._faultyGets++;
   }

   public void addSuccessfulGet( int time ) {
      _stats._sumSuccessfulRequestTimes += time;
      _stats._successfulGets++;
   }

   public void close() {
      // When HttpClient instance is no longer needed, 
      // shut down the connection manager to ensure
      // immediate deallocation of all system resources
      if ( _httpClient != null ) {
         _httpClient.getConnectionManager().shutdown();
      }
   }

   @Override
   public int compareTo( Proxy o ) {
      int errors1 = _stats.getFaultyGets();
      int errors2 = o._stats.getFaultyGets();
      //      if ( errors1 != errors2 ) return errors1 < errors2 ? -1 : 1;

      // weird sorting: if no successful gets yet, put a proxy to the front
      int gets1 = _stats.getSuccessfulGets();
      int gets2 = o._stats.getSuccessfulGets();
      if ( gets1 == 0 && errors1 == 0 && gets2 > 0 ) {
         return -1;
      }
      if ( gets2 == 0 && errors2 == 0 && gets1 > 0 ) {
         return 1;
      }

      float s1 = (float)(_stats.getAverageSuccessfulRequestTime() / Math.log10(gets1 + 9) * (1f + (errors1 / 2f)));
      float s2 = (float)(_stats.getAverageSuccessfulRequestTime() / Math.log10(gets2 + 9) * (1f + (errors2 / 2f)));
      return Float.compare(s1, s2);

      // otherwise sort by number of successful gets descending 
      //      return gets1 < gets2 ? 1 : (gets1 == gets2 ? 0 : -1);
   }

   @Override
   public boolean equals( Object obj ) {
      if ( this == obj ) {
         return true;
      }
      if ( obj == null ) {
         return false;
      }
      if ( getClass() != obj.getClass() ) {
         return false;
      }
      Proxy other = (Proxy)obj;
      if ( _address == null ) {
         if ( other._address != null ) {
            return false;
         }
      } else if ( !_address.equals(other._address) ) {
         return false;
      }
      return true;
   }

   public ProxyAddress getAddress() {
      return _address;
   }

   public HttpClient getHttpClient() {
      _stats._clientAccesses++;
      if ( _httpClient != null ) {
         return _httpClient;
      }

      HttpClientFactory httpClientFactory = new HttpClientFactory();
      httpClientFactory.setConnectionTimeout(60 * 1000);
      httpClientFactory.setSoTimeout(60 * 1000);
      if ( _userAgent != null ) {
         httpClientFactory.setUserAgent(_userAgent);
      }
      if ( _authenticationUser != null ) {
         httpClientFactory.setUser(_authenticationUser);
         httpClientFactory.setPassword(_authenticationPassword);
      }
      httpClientFactory.setNeverRetryHttpRequests(true);
      _httpClient = httpClientFactory.create();

      HttpParams params = _httpClient.getParams();
      params.setParameter(ConnRoutePNames.DEFAULT_PROXY, _proxyHost);

      return _httpClient;
   }

   public Stats getStats() {
      return _stats;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((_address == null) ? 0 : _address.hashCode());
      return result;
   }

   public boolean isInsane() {
      return _insane;
   }

   public void measureLatency() {
      HttpClient httpClient = getHttpClient();

      HttpGet req = new HttpGet("/");

      long t = System.currentTimeMillis();
      try {
         HttpResponse rsp = httpClient.execute(_latencyTestHost, req);
         int responseCode = rsp.getStatusLine().getStatusCode();
         if ( responseCode != 200 ) {
            throw new RuntimeException("Unexpected response code: " + responseCode);
         }
         _stats._firstByteLatency = System.currentTimeMillis() - t;
         HttpEntity entity = rsp.getEntity();

         String webpage = EntityUtils.toString(entity);
         _stats._lastByteLatency = System.currentTimeMillis() - t;

         _insane = false;
         for ( Pattern p : _sanePatterns ) {
            if ( !p.matcher(webpage).find() ) {
               _insane = true;
            }
         }
         for ( Pattern p : _insanePatterns ) {
            if ( p.matcher(webpage).find() ) {
               _insane = true;
            }
         }
      }
      catch ( Exception argh ) {
         _log.debug("Failed to measure latency of proxy " + _address + " - maybe it's offline?", argh);
      }
   }

   public void setAuthenticationPassword( String authenticationPassword ) {
      _authenticationPassword = authenticationPassword;
   }

   public void setAuthenticationUser( String authenticationUser ) {
      _authenticationUser = authenticationUser;
   }

   public void setHttpClient( HttpClient httpClient ) {
      _httpClient = httpClient;
   }

   public void setInsanePatterns( List<Pattern> insanePatterns ) {
      _insanePatterns = insanePatterns == null ? DEFAULT_INSANE_PATTERNS : insanePatterns;
   }

   public void setLatencyTestHost( HttpHost latencyTestHost ) {
      _latencyTestHost = latencyTestHost == null ? DEFAULT_LATENCY_TEST_HOST : latencyTestHost;
   }

   public void setSanePatterns( List<Pattern> sanePatterns ) {
      _sanePatterns = sanePatterns == null ? DEFAULT_SANE_PATTERNS : sanePatterns;
   }

   public void setUserAgent( String userAgent ) {
      _userAgent = userAgent;
   }

   @Override
   public String toString() {
      return _address + ", " + _stats;
   }

   @Override
   protected void finalize() throws Throwable {
      close();
      super.finalize();
   }


   public class Stats {

      private long _firstByteLatency          = -1;
      private long _lastByteLatency           = -1;

      private int  _clientAccesses            = 0;
      private int  _successfulGets            = 0;
      private int  _faultyGets                = 0;
      private int  _sumSuccessfulRequestTimes = 0;
      private int  _sumFaultyRequestTimes     = 0;


      public int getAverageFaultyRequestTime() {
         return _faultyGets == 0 ? 0 : _sumFaultyRequestTimes / _faultyGets;
      }

      public int getAverageRequestTime() {
         return getTotalGets() == 0 ? 0 : (_sumFaultyRequestTimes + _sumSuccessfulRequestTimes) / getTotalGets();
      }

      public int getAverageSuccessfulRequestTime() {
         return _successfulGets == 0 ? 0 : _sumSuccessfulRequestTimes / _successfulGets;
      }

      public int getClientAccesses() {
         return _clientAccesses;
      }

      public float getFaultRatio() {
         return getFaultyGets() / (float)getTotalGets();
      }

      public int getFaultyGets() {
         return _faultyGets;
      }

      public long getFirstByteLatency() {
         if ( _firstByteLatency < 0 ) {
            measureLatency();
         }
         return _firstByteLatency;
      }

      public long getLastByteLatency() {
         if ( _lastByteLatency < 0 ) {
            measureLatency();
         }
         return _lastByteLatency;
      }

      public int getSuccessfulGets() {
         return _successfulGets;
      }

      public int getTotalGets() {
         return getSuccessfulGets() + getFaultyGets();
      }

      @Override
      public String toString() {
         return "gets: " + getTotalGets() + ", " + //
            "firstByteLatency: " + _firstByteLatency + " ms, " + //
            "lastByteLatency: " + _lastByteLatency + " ms, " + //
            "clientAccesses: " + _clientAccesses + ", " + //
            "faultRatio: " + (Float.isNaN(getFaultRatio()) ? "0" : NumberFormat.getPercentInstance().format(getFaultRatio())) + ", " + //
            "averageRequestTime: " + getAverageRequestTime() + " ms, " + //
            "averageSuccessfulRequestTime: " + getAverageSuccessfulRequestTime() + " ms, " + //
            "averageFaultyRequestTime: " + getAverageFaultyRequestTime() + " ms, " + //
            "insane: " + _insane;
      }
   }
}
