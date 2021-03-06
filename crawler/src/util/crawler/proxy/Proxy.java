package util.crawler.proxy;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.crawler.proxy.ProxyList.ProxyAddress;
import util.http.HttpClientFactory;


public class Proxy implements Comparable<Proxy> {

   public static final HttpHost      DEFAULT_LATENCY_TEST_HOST = new HttpHost("www.chrono24.com");
   public static final List<Pattern> DEFAULT_SANE_PATTERNS     = Arrays.asList(Pattern.compile("chrono24"));
   public static final List<Pattern> DEFAULT_INSANE_PATTERNS   = Arrays.asList(Pattern.compile("Page Not Responding"),   //
      Pattern.compile("unimed"),                                                                                         //
      Pattern.compile("samair"),                                                                                         //
      Pattern.compile("404 Not Found"),                                                                                  //
      Pattern.compile("Hickory Orchards"),                                                                               //
      Pattern.compile("<![CDATA[<body></body>]]>"),                                                                      //
      Pattern.compile("Acceso denegado"),                                                                                //
      Pattern.compile("CoDeeN CAPTCHA"),                                                                                 //
      Pattern.compile("Access denied"),                                                                                  //
      Pattern.compile("Barracuda Web Filter"),                                                                           //
      Pattern.compile("Barracuda410"),                                                                                   //
      Pattern.compile("Now go away."),                                                                                   //
      Pattern.compile("www.caalbor.adv.br")                                                                              //
                                                                 );

   public static final Comparator<Proxy> FASTEST_FIRST_COMPARATOR = ( p1, p2 ) -> {
      int errors1 = p1._stats.getFaultyGets();
      int errors2 = p2._stats.getFaultyGets();
      //      if ( errors1 != errors2 ) return errors1 < errors2 ? -1 : 1;

      // weird sorting: if no successful gets yet, put a proxy to the front
      int gets1 = p1._stats.getSuccessfulGets();
      int gets2 = p2._stats.getSuccessfulGets();
      if ( gets1 == 0 && errors1 == 0 && gets2 > 0 ) {
         return -1;
      }
      if ( gets2 == 0 && errors2 == 0 && gets1 > 0 ) {
         return 1;
      }

      float s1 = (float)(p1._stats.getAverageSuccessfulRequestTime() / Math.log10(gets1 + 9) * (1f + (errors1 / 2f)));
      float s2 = (float)(p2._stats.getAverageSuccessfulRequestTime() / Math.log10(gets2 + 9) * (1f + (errors2 / 2f)));
      return Float.compare(s1, s2);
   };

   public static final Comparator<Proxy> LOAD_DISTRIBUTING_COMPARATOR = ( p1, p2 ) -> {
      int errors1 = p1._stats.getFaultyGets();
      int errors2 = p2._stats.getFaultyGets();

      int gets1 = p1._stats.getSuccessfulGets();
      int gets2 = p2._stats.getSuccessfulGets();

      int score1 = gets1+errors1*50;
      int score2 = gets2+errors2*50;
      return Integer.compare(score1, score2);
   };

   private static Logger _log = LoggerFactory.getLogger(Proxy.class);


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


   private final ProxyAddress _address;
   private HttpHost           _proxyHost;

   private CloseableHttpClient _httpClient;

   private Stats _stats;

   private HttpHost      _latencyTestHost = DEFAULT_LATENCY_TEST_HOST;
   private List<Pattern> _sanePatterns    = DEFAULT_SANE_PATTERNS;
   private List<Pattern> _insanePatterns  = DEFAULT_INSANE_PATTERNS;

   private boolean _insane;
   private String  _userAgent;

   private String _authenticationUser;
   private String _authenticationPassword;

   private int _socketTimeout     = 60000;
   private int _connectionTimeout = 60000;


   public Proxy( ProxyAddress address ) {
      this(address, "http");
   }

   /**
    * @param scheme either "http" or "https"
    */
   public Proxy( ProxyAddress address, String scheme ) {
      _address = address;
      _proxyHost = new HttpHost(_address._ip, _address._port, scheme);
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
      return FASTEST_FIRST_COMPARATOR.compare(this, o);
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

   public CloseableHttpClient getHttpClient() {
      _stats._clientAccesses++;
      if ( _httpClient != null ) {
         return _httpClient;
      }
      _httpClient = createHttpClient(_socketTimeout, _connectionTimeout);

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
      measureLatency(60000);
   }

   public void measureLatency( int maxLatencyInMillis ) {
      CloseableHttpClient httpClient = createHttpClient(maxLatencyInMillis, maxLatencyInMillis);

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
         _stats._firstByteLatency = maxLatencyInMillis + 1;
         _stats._lastByteLatency = maxLatencyInMillis + 1;
      }

      HttpClientFactory.close(httpClient);
   }

   public void setAuthenticationPassword( String authenticationPassword ) {
      _authenticationPassword = authenticationPassword;
   }

   public void setAuthenticationUser( String authenticationUser ) {
      _authenticationUser = authenticationUser;
   }

   public void setConnectionTimeout( int connectionTimeout ) {
      _connectionTimeout = connectionTimeout;
   }

   public void setHttpClient( CloseableHttpClient httpClient ) {
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

   public void setSocketTimeout( int socketTimeout ) {
      _socketTimeout = socketTimeout;
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

   private CloseableHttpClient createHttpClient( int socketTimeout, int connectionTimeout ) {
      HttpClientFactory httpClientFactory = new HttpClientFactory();
      httpClientFactory.setConnectionTimeout(connectionTimeout);
      httpClientFactory.setSoTimeout(socketTimeout);
      if ( _userAgent != null ) {
         httpClientFactory.setUserAgent(_userAgent);
      }
      if ( _authenticationUser != null ) {
         httpClientFactory.setUser(_authenticationUser);
         httpClientFactory.setPassword(_authenticationPassword);
      }
      httpClientFactory.setNeverRetryHttpRequests(true);
      httpClientFactory.setProxy(_proxyHost);

      return httpClientFactory.create();
   }


   public class Stats {

      private long _firstByteLatency = -1;
      private long _lastByteLatency  = -1;

      private int _clientAccesses            = 0;
      private int _successfulGets            = 0;
      private int _faultyGets                = 0;
      private int _sumSuccessfulRequestTimes = 0;
      private int _sumFaultyRequestTimes     = 0;


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
