package util.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.apache.commons.codec.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.util.PublicSuffixMatcher;
import org.apache.http.conn.util.PublicSuffixMatcherLoader;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.cookie.DefaultCookieSpecProvider;
import org.apache.http.impl.cookie.DefaultCookieSpecProvider.CompatibilityLevel;
import org.apache.http.impl.cookie.IgnoreSpecProvider;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.string.StringTool;
import util.time.TimeUtils;


public class HttpClientFactory {

   public static final int    DEFAULT_VALUE_SOCKET_TIMEOUT     = 31000;
   public static final int    DEFAULT_VALUE_CONNECTION_TIMEOUT = 31000;
   public static final String DEFAULT_VALUE_USER_AGENT         = "Googlebot/2.1 (+http://www.google.com/bot.html)";

   private static Logger _log = LoggerFactory.getLogger(HttpClientFactory.class);

   private static final Pattern HTML_CHARSET_DECLARATION = Pattern.compile("(?i)(?:charset|encoding)=[\"']?(.*?)[\"'/>]");

   private static IdleConnectionMonitorThread _idleConnectionMonitorThread;

   public static void close( HttpClient httpClient ) {
      if ( httpClient instanceof CloseableHttpClient ) {
         try {
            ((CloseableHttpClient)httpClient).close();
         }
         catch ( IOException argh ) {
            _log.warn("Failed to close HttpClient", argh);
         }
      } else {
         httpClient.getConnectionManager().shutdown();
      }
   }

   public static HttpGet createGet( String url, String... queryParams ) throws UnsupportedEncodingException {
      List<NameValuePair> params = new ArrayList<>();
      for ( int i = 0, length = queryParams.length; i < length; i += 2 ) {
         params.add(new BasicNameValuePair(queryParams[i], queryParams[i + 1]));
      }
      String queryString = URLEncodedUtils.format(params, "UTF-8");
      HttpGet get = new HttpGet(url + "?" + queryString);
      return get;
   }

   public static HttpContext createHttpContext( boolean addCookieStore ) {
      HttpContext localContext = new BasicHttpContext();
      if ( addCookieStore ) {
         localContext.setAttribute(HttpClientContext.COOKIE_STORE, new BasicCookieStore());
      }
      return localContext;
   }

   /**
    * @return if the post has parameters, the result is castable to HttpEntityEnclosingRequestBase
    */
   public static HttpUriRequest createPost( String url, String... postParams ) throws UnsupportedEncodingException {
      int questionMarkIndex = url.indexOf('?');
      if ( questionMarkIndex > 0 && questionMarkIndex + 1 < url.length() ) {
         String params = url.substring(questionMarkIndex + 1);
         url = url.substring(0, questionMarkIndex);
         String[] paramsTokenized = StringTool.tokenize(params, "&", false);
         String[] newPostParams = new String[postParams.length + paramsTokenized.length * 2];
         System.arraycopy(postParams, 0, newPostParams, 0, postParams.length);
         for ( int i = postParams.length, length = newPostParams.length, j = 0; i < length; i += 2 ) {
            String[] p = StringTool.split(paramsTokenized[j++], '=');
            newPostParams[i] = p[0];
            newPostParams[i + 1] = p.length > 1 ? p[1] : "";
         }
         postParams = newPostParams;
      }
      RequestBuilder postBuilder = RequestBuilder.post(url);
      for ( int i = 0, length = postParams.length; i < length; i += 2 ) {
         postBuilder.addParameter(new BasicNameValuePair(postParams[i], postParams[i + 1]));
      }
      postBuilder.setCharset(Charsets.UTF_8);
      return postBuilder.build();
   }

   public static Date getLastModified( HttpClient httpClient, URL url ) throws ClientProtocolException, IOException, URISyntaxException {
      HttpHead httpHead = new HttpHead(url.toURI());
      HttpContext localContext = createHttpContext(false);
      HttpResponse response = httpClient.execute(httpHead, localContext);

      Header lastModified = response.getFirstHeader("Last-Modified");

      if ( lastModified == null ) {
         return null;
      }

      return DateUtils.parseDate(lastModified.getValue());
   }

   public static String readPage( HttpResponse response ) throws Exception {
      return readPage(response, null);
   }

   public static String readPage( HttpResponse response, String pageEncoding ) throws Exception {
      HttpEntity entity = response.getEntity();
      InputStream content = entity.getContent();
      byte[] bytes = IOUtils.toByteArray(content);
      content.close();

      if ( pageEncoding != null ) {
         return new String(bytes, pageEncoding);
      }

      String charset = EntityUtils.getContentCharSet(entity);
      if ( charset == null ) {
         charset = StandardCharsets.UTF_8.name();
      }
      String page = new String(bytes, charset);

      Matcher matcher = HTML_CHARSET_DECLARATION.matcher(page);
      if ( matcher.find() ) {
         charset = matcher.group(1);
         charset = sanitizeCharset(charset);
         try {
            page = new String(bytes, charset);
            if ( page.length() > 0 && page.charAt(0) == 0xfeff ) {
               // remove BOM
               page = page.substring(1);
            }
         }
         catch ( UnsupportedEncodingException argh ) {
            _log.warn("Failed to decode encoding " + charset, argh);
         }
      }

      return page;
   }

   /**
    * @see http://dev.w3.org/html5/spec/Overview.html#character-encodings-0
    */
   private static String sanitizeCharset( String charset ) {
      if ( charset.equalsIgnoreCase("US-ASCII") ) {
         return "cp1252";
      }
      if ( charset.equalsIgnoreCase("ISO-8859-1") ) {
         return "cp1252";
      }
      if ( charset.equalsIgnoreCase("ISO-8859-9") ) {
         return "cp1254";
      }
      if ( charset.equalsIgnoreCase("ISO-8859-11") ) {
         return "cp874";
      }
      return charset;
   }

   int                         _soTimeout               = DEFAULT_VALUE_SOCKET_TIMEOUT;
   int                         _connectionTimeout       = DEFAULT_VALUE_CONNECTION_TIMEOUT;
   boolean                     _useExpectContinue       = true;
   String                      _userAgent               = DEFAULT_VALUE_USER_AGENT;
   boolean                     _gzipSupport             = true;
   boolean                     _tcpNodelay              = false;
   boolean                     _executeRedirects        = true;
   String                      _user                    = null;
   String                      _password                = null;
   boolean                     _trustAllSsl             = true;
   boolean                     _neverRetryHttpRequests  = false;
   boolean                     _useCookies              = true;
   int                         _maxConnections          = 10;
   Consumer<HttpClientBuilder> _clientBuilderConfigurer = null;
   HttpHost                    _proxyHost;

   public CloseableHttpClient create() {

      /* we need to allow broken cookie expire headers, so we have to tune the CookieSpecs.DEFAULT config */
      PublicSuffixMatcher publicSuffixMatcher = PublicSuffixMatcherLoader.getDefault();
      Registry<CookieSpecProvider> cookieSpecRegistry = RegistryBuilder.<CookieSpecProvider>create() //
            .register(CookieSpecs.DEFAULT, new DefaultCookieSpecProvider(CompatibilityLevel.DEFAULT, publicSuffixMatcher,
                  new String[] { "EEE, dd-MMM-yy HH:mm:ss z", "EEE, dd MMM yy HH:mm:ss z" }, false)) //
            .register(CookieSpecs.IGNORE_COOKIES, new IgnoreSpecProvider())//
            .build();

      RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
      requestConfigBuilder.setConnectTimeout(_connectionTimeout); // the time to establish the connection with the remote host
      requestConfigBuilder.setConnectionRequestTimeout(_connectionTimeout); // the time to wait for a connection from the connection manager/pool
      requestConfigBuilder.setSocketTimeout(
            _soTimeout); // the time waiting for data – after the connection was established; maximum time of inactivity between two data packets
      requestConfigBuilder.setContentCompressionEnabled(_gzipSupport);
      requestConfigBuilder.setRedirectsEnabled(_executeRedirects);
      requestConfigBuilder.setExpectContinueEnabled(_useExpectContinue);
      if ( _useCookies ) {
         requestConfigBuilder.setCookieSpec(CookieSpecs.DEFAULT);
      } else {
         requestConfigBuilder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
      }

      HttpClientBuilder clientBuilder = HttpClients.custom();
      clientBuilder.setDefaultCookieSpecRegistry(cookieSpecRegistry);
      clientBuilder.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(_soTimeout).setTcpNoDelay(_tcpNodelay).build());
      clientBuilder.setDefaultRequestConfig(requestConfigBuilder.build());
      clientBuilder.setUserAgent(_userAgent);
      if ( _neverRetryHttpRequests ) {
         clientBuilder.disableAutomaticRetries();
      }
      if ( _user != null && _password != null ) {
         CredentialsProvider provider = new BasicCredentialsProvider();
         UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(_user, _password);
         provider.setCredentials(AuthScope.ANY, credentials);
         clientBuilder.setDefaultCredentialsProvider(provider);
      }
      if ( _proxyHost != null ) {
         clientBuilder.setRoutePlanner(new DefaultProxyRoutePlanner(_proxyHost));
      }

      PoolingHttpClientConnectionManager connectionManager;
      if ( _trustAllSsl ) {
         Registry<ConnectionSocketFactory> socketFactoryRegistry = null;
         try {
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustStrategy() {

               @Override
               public boolean isTrusted( X509Certificate[] chain, String authType ) throws CertificateException {
                  return true;
               }
            }).build();
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
            socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()//
                  .register("http", PlainConnectionSocketFactory.getSocketFactory())//
                  .register("https", sslSocketFactory)//
                  .build();
         }
         catch ( Exception argh ) {
            _log.error("Failed to build ssl context", argh);
         }
         connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
      } else {
         connectionManager = new PoolingHttpClientConnectionManager();
      }
      connectionManager.setMaxTotal(_maxConnections * 10);
      connectionManager.setDefaultMaxPerRoute(_maxConnections);
      clientBuilder.setConnectionManager(connectionManager);

      if ( _idleConnectionMonitorThread == null ) {
         _idleConnectionMonitorThread = new IdleConnectionMonitorThread();
         _idleConnectionMonitorThread.start();
      }
      _idleConnectionMonitorThread.add(connectionManager);

      clientBuilder.setKeepAliveStrategy(new MyConnectionKeepAliveStrategy());
      clientBuilder.setRedirectStrategy(new Redirector());

      if ( _clientBuilderConfigurer != null ) {
         _clientBuilderConfigurer.accept(clientBuilder);
      }
      return clientBuilder.build();

   }

   /** use this to configure anything in the HttpClientBuilder that is not provided here */
   public HttpClientFactory setClientBuilderConfigurer( Consumer<HttpClientBuilder> clientBuilderConfigurer ) {
      _clientBuilderConfigurer = clientBuilderConfigurer;
      return this;
   }

   /** in ms */
   public HttpClientFactory setConnectionTimeout( int connectionTimeout ) {
      _connectionTimeout = connectionTimeout;
      return this;
   }

   public HttpClientFactory setExecuteRedirects( boolean executeRedirects ) {
      _executeRedirects = executeRedirects;
      return this;
   }

   public HttpClientFactory setGzipSupport( boolean gzipSupport ) {
      _gzipSupport = gzipSupport;
      return this;
   }

   public HttpClientFactory setMaxConnections( int maxConnections ) {
      _maxConnections = maxConnections;
      return this;
   }

   public HttpClientFactory setNeverRetryHttpRequests( boolean neverRetry ) {
      _neverRetryHttpRequests = neverRetry;
      return this;
   }

   public HttpClientFactory setPassword( String password ) {
      _password = password;
      return this;
   }

   public HttpClientFactory setProxy( String proxyIP, int proxyPort ) {
      _proxyHost = new HttpHost(proxyIP, proxyPort);
      return this;
   }

   public HttpClientFactory setProxy( HttpHost proxyHost ) {
      _proxyHost = proxyHost;
      return this;
   }

   /** in ms */
   public HttpClientFactory setSoTimeout( int soTimeout ) {
      _soTimeout = soTimeout;
      return this;
   }

   public HttpClientFactory setTcpNodelay( boolean tcpNodelay ) {
      _tcpNodelay = tcpNodelay;
      return this;
   }

   public HttpClientFactory setTrustAllSsl( boolean trustAllSsl ) {
      _trustAllSsl = trustAllSsl;
      return this;
   }

   public HttpClientFactory setUseCookies( boolean useCookies ) {
      _useCookies = useCookies;
      return this;
   }

   /** @see {@link org.apache.http.params.CoreProtocolPNames#USE_EXPECT_CONTINUE} */
   public HttpClientFactory setUseExpectContinue( boolean useExpectContinue ) {
      _useExpectContinue = useExpectContinue;
      return this;
   }

   public HttpClientFactory setUser( String user ) {
      _user = user;
      return this;
   }

   public HttpClientFactory setUserAgent( String userAgent ) {
      _userAgent = userAgent;
      return this;
   }

   /**
    * One of the major shortcomings of the classic blocking I/O model is that the network socket can react to I/O events only when blocked in an I/O operation. When a connection is released back to the manager, it can be kept alive however it is unable to monitor the status of the socket and react to any I/O events. If the connection gets closed on the server side, the client side connection is unable to detect the change in the connection state (and react appropriately by closing the socket on its end).
    *
    * HttpClient tries to mitigate the problem by testing whether the connection is 'stale', that is no longer valid because it was closed on the server side, prior to using the connection for executing an HTTP request. The stale connection check is not 100% reliable. The only feasible solution that does not involve a one thread per socket model for idle connections is a dedicated monitor thread used to evict connections that are considered expired due to a long period of inactivity. The monitor thread can periodically call ClientConnectionManager#closeExpiredConnections() method to close all expired connections and evict closed connections from the pool. It can also optionally call ClientConnectionManager#closeIdleConnections() method to close all connections that have been idle over a given period of time.
    * @see https://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html
    */
   static class IdleConnectionMonitorThread extends Thread {

      Logger _log = LoggerFactory.getLogger(IdleConnectionMonitorThread.class);

      private          WeakHashMap<HttpClientConnectionManager, Boolean> _conMans = new WeakHashMap<>();
      private volatile boolean                                           _shutdown;

      public IdleConnectionMonitorThread() {
         super(IdleConnectionMonitorThread.class.getSimpleName());
         setDaemon(true);
      }

      public void add( HttpClientConnectionManager ccm ) {
         synchronized ( _conMans ) {
            _conMans.put(ccm, Boolean.TRUE);
         }
      }

      @Override
      public void run() {
         _log.debug(getName() + " started");
         while ( !_shutdown ) {
            TimeUtils.sleepQuietlySeconds(5);

            synchronized ( _conMans ) {
               for ( HttpClientConnectionManager ccm : _conMans.keySet() ) {
                  if ( ccm != null ) {
                     // Close expired connections
                     ccm.closeExpiredConnections();
                     // Optionally, close connections
                     // that have been idle longer than 30 sec
                     ccm.closeIdleConnections(30, TimeUnit.SECONDS);
                  }
               }
            }
         }
      }

      public void shutdown() {
         _shutdown = true;
         synchronized ( this ) {
            notifyAll();
         }
      }

   }


   /**
    * Quoting the HttpClient 4.3.3. reference: “If the Keep-Alive header is not present in the response, HttpClient assumes the connection can be kept alive indefinitely.” (See the HttpClient Reference).
    *
    * To get around this, and be able to manage dead connections we need a customized strategy implementation and build it into the HttpClient.
    *
    * @see https://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html
    */
   static class MyConnectionKeepAliveStrategy implements ConnectionKeepAliveStrategy {

      @Override
      public long getKeepAliveDuration( HttpResponse response, HttpContext context ) {
         HeaderElementIterator it = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
         while ( it.hasNext() ) {
            HeaderElement he = it.nextElement();
            String param = he.getName();
            String value = he.getValue();
            if ( value != null && param.equalsIgnoreCase("timeout") ) {
               return Long.parseLong(value) * 1000;
            }
         }
         return 5 * 1000;
      }
   }


   static class Redirector extends DefaultRedirectStrategy {

      @Override
      public boolean isRedirected( HttpRequest request, HttpResponse response, HttpContext context ) throws ProtocolException {
         boolean isRedirect = false;
         isRedirect = super.isRedirected(request, response, context);
         if ( !isRedirect ) {
            int responseCode = response.getStatusLine().getStatusCode();
            if ( responseCode == 301 || responseCode == 302 ) {
               return true;
            }
         }
         return isRedirect;
      }
   }

}
