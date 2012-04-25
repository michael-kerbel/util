package util.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.AllClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionManagerFactory;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.impl.cookie.DateParseException;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;


public class HttpClientFactory {

   public static final String   PARAM_KEY_SOCKET_TIMEOUT         = "HttpClientFactory.soTimeout";
   public static final String   PARAM_KEY_CONNECTION_TIMEOUT     = "HttpClientFactory.connectionTimeout";
   public static final String   PARAM_KEY_USER_AGENT             = "HttpClientFactory.userAgent";

   public static final int      DEFAULT_VALUE_SOCKET_TIMEOUT     = 31000;
   public static final int      DEFAULT_VALUE_CONNECTION_TIMEOUT = 31000;
   public static final String   DEFAULT_VALUE_USER_AGENT         = "Googlebot/2.1 (+http://www.google.com/bot.html)";

   private static Logger        _log                             = Logger.getLogger(HttpClientFactory.class);

   private static final Pattern HTML_CHARSET_DECLARATION         = Pattern.compile("(?i)(?:charset|encoding)=[\"']?(.*?)[\"'/>]");


   public static void close( HttpClient httpClient ) {
      httpClient.getConnectionManager().shutdown();
   }

   public static HttpGet createGet( String url, String... queryParams ) throws UnsupportedEncodingException {
      List<NameValuePair> params = new ArrayList<NameValuePair>();
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
         localContext.setAttribute(ClientContext.COOKIE_STORE, new BasicCookieStore());
      }
      return localContext;
   }

   public static HttpPost createPost( String url, String... postParams ) throws UnsupportedEncodingException {
      HttpPost post = new HttpPost(url);
      List<NameValuePair> formparams = new ArrayList<NameValuePair>();
      for ( int i = 0, length = postParams.length; i < length; i += 2 ) {
         formparams.add(new BasicNameValuePair(postParams[i], postParams[i + 1]));
      }
      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
      post.setEntity(entity);
      return post;
   }

   public static Date getLastModified( HttpClient httpClient, URL url ) throws ClientProtocolException, IOException, DateParseException, URISyntaxException {
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

      if ( pageEncoding != null ) {
         return new String(bytes, pageEncoding);
      }

      String charset = EntityUtils.getContentCharSet(entity);
      if ( charset == null ) {
         charset = HTTP.DEFAULT_CONTENT_CHARSET;
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

   private static void addGZipSupport( DefaultHttpClient httpclient ) {
      httpclient.addRequestInterceptor(new HttpRequestInterceptor() {

         public void process( final HttpRequest request, final HttpContext context ) throws HttpException, IOException {
            if ( !request.containsHeader("Accept-Encoding") ) {
               request.addHeader("Accept-Encoding", "gzip");
            }
         }

      });

      httpclient.addResponseInterceptor(new HttpResponseInterceptor() {

         public void process( final HttpResponse response, final HttpContext context ) throws HttpException, IOException {
            HttpEntity entity = response.getEntity();
            if ( entity == null ) {
               return;
            }
            Header ceheader = entity.getContentEncoding();
            if ( ceheader == null ) {
               return;
            }

            HeaderElement[] codecs = ceheader.getElements();
            for ( int i = 0; i < codecs.length; i++ ) {
               if ( codecs[i].getName().equalsIgnoreCase("gzip") ) {
                  MDC.put("gzip", "gz");
                  response.setEntity(new GzipDecompressingEntity(response.getEntity()));
                  return;
               }
            }

            MDC.put("gzip", "");
         }

      });
   }

   private static int getIntParameter( String key, int defaultValue ) {
      String value = System.getProperty(key);
      if ( value != null ) {
         try {
            return Integer.parseInt(value);
         }
         catch ( Exception argh ) {
            _log.warn("Failed to parse system property '" + key + "' as int: " + value);
         }
      }
      return defaultValue;
   }

   private static String getParameter( String key, String defaultValue ) {
      return System.getProperty(key, defaultValue);
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


   int         _soTimeout              = getIntParameter(PARAM_KEY_SOCKET_TIMEOUT, DEFAULT_VALUE_SOCKET_TIMEOUT);
   int         _connectionTimeout      = getIntParameter(PARAM_KEY_CONNECTION_TIMEOUT, DEFAULT_VALUE_CONNECTION_TIMEOUT);
   HttpVersion _httpVersion            = HttpVersion.HTTP_1_1;
   String      _contentCharset         = "UTF-8";
   boolean     _useExpectContinue      = true;
   String      _userAgent              = getParameter(PARAM_KEY_USER_AGENT, DEFAULT_VALUE_USER_AGENT);
   boolean     _gzipSupport            = true;
   boolean     _tcpNodelay             = false;
   boolean     _executeRedirects       = true;
   String      _user                   = null;
   String      _password               = null;
   boolean     _trustAllSsl            = true;
   boolean     _neverRetryHttpRequests = false;
   boolean     _useCookies             = true;
   int         _maxConnections         = 10;


   public HttpClient create() {
      // general setup
      SchemeRegistry supportedSchemes = new SchemeRegistry();

      // Register the "http" and "https" protocol schemes, they are
      // required by the default operator to look up socket factories.
      supportedSchemes.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
      supportedSchemes.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

      // prepare parameters
      HttpParams params = new BasicHttpParams();
      HttpConnectionParams.setSoTimeout(params, _soTimeout);
      HttpConnectionParams.setConnectionTimeout(params, _connectionTimeout);
      HttpConnectionParams.setTcpNoDelay(params, _tcpNodelay);
      HttpProtocolParams.setVersion(params, _httpVersion);
      HttpProtocolParams.setContentCharset(params, _contentCharset);
      HttpProtocolParams.setUseExpectContinue(params, _useExpectContinue);
      HttpProtocolParams.setUserAgent(params, _userAgent);
      HttpClientParams.setRedirecting(params, _executeRedirects);
      HttpClientParams.setCookiePolicy(params, CookiePolicy.BROWSER_COMPATIBILITY);
      if ( !_useCookies ) {
         HttpClientParams.setCookiePolicy(params, CookiePolicy.IGNORE_COOKIES);
      }
      params.setParameter(AllClientPNames.CONNECTION_MANAGER_FACTORY_CLASS_NAME, ThreadSafeConnManagerFactory.class.getName());
      params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(_maxConnections));

      // http://hc.apache.org/httpcomponents-client/tutorial/html/ch02.html
      // The stale connection check is not 100% reliable and adds 10 to 30 ms overhead to each request execution
      // instead we start the IdleConnectionMonitor thread as supposed in the tutorial
      HttpConnectionParams.setStaleCheckingEnabled(params, false);

      DefaultHttpClient httpclient = new DefaultHttpClient(params);

      if ( _trustAllSsl ) {
         httpclient = setTrustManager(httpclient);
      }

      if ( _user != null && _password != null ) {
         httpclient.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(_user, _password));
      }

      if ( _gzipSupport ) {
         addGZipSupport(httpclient);
      }

      if ( _neverRetryHttpRequests ) {
         httpclient.setHttpRequestRetryHandler(new DontRetryHandler());
      }

      return httpclient;
   }

   /** in ms */
   public void setConnectionTimeout( int connectionTimeout ) {
      _connectionTimeout = connectionTimeout;
   }

   public void setContentCharset( String contentCharset ) {
      _contentCharset = contentCharset;
   }

   public void setExecuteRedirects( boolean executeRedirects ) {
      _executeRedirects = executeRedirects;
   }

   public void setGzipSupport( boolean gzipSupport ) {
      _gzipSupport = gzipSupport;
   }

   public void setHttpVersion( HttpVersion httpVersion ) {
      _httpVersion = httpVersion;
   }

   public void setMaxConnections( int maxConnections ) {
      _maxConnections = maxConnections;
   }

   public void setNeverRetryHttpRequests( boolean neverRetry ) {
      _neverRetryHttpRequests = neverRetry;
   }

   public void setPassword( String password ) {
      _password = password;
   }

   /** in ms */
   public void setSoTimeout( int soTimeout ) {
      _soTimeout = soTimeout;
   }

   public void setTcpNodelay( boolean tcpNodelay ) {
      _tcpNodelay = tcpNodelay;
   }

   public void setTrustAllSsl( boolean trustAllSsl ) {
      _trustAllSsl = trustAllSsl;
   }

   public void setUseCookies( boolean useCookies ) {
      _useCookies = useCookies;
   }

   /** @see {@link org.apache.http.params.CoreProtocolPNames#USE_EXPECT_CONTINUE} */
   public void setUseExpectContinue( boolean useExpectContinue ) {
      _useExpectContinue = useExpectContinue;
   }

   public void setUser( String user ) {
      _user = user;
   }

   public void setUserAgent( String userAgent ) {
      _userAgent = userAgent;
   }

   private DefaultHttpClient setTrustManager( DefaultHttpClient httpclient ) {
      try {
         SSLContext ctx = SSLContext.getInstance("TLS");
         ctx.init(null, new TrustManager[] { new TrustAllSslTrustManager() }, null);
         SSLSocketFactory ssf = new SSLSocketFactory(ctx);
         ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
         ClientConnectionManager ccm = httpclient.getConnectionManager();
         SchemeRegistry sr = ccm.getSchemeRegistry();
         sr.register(new Scheme("https", ssf, 443));
         return new DefaultHttpClient(ccm, httpclient.getParams());
      }
      catch ( Exception ex ) {
         _log.error("Failed to equip httpclient with TrustAllSslTrustManager.", ex);
         return null;
      }

   }


   public static class ThreadSafeConnManagerFactory implements ClientConnectionManagerFactory {

      public ClientConnectionManager newInstance( HttpParams params, SchemeRegistry schemeRegistry ) {
         ClientConnectionManager connectionManager = new ThreadSafeClientConnManager(params, schemeRegistry);
         new IdleConnectionMonitorThread(connectionManager).start();
         return connectionManager;
      }

   }

   static class DontRetryHandler implements HttpRequestRetryHandler {

      public boolean retryRequest( IOException exception, int executionCount, HttpContext context ) {
         return false;
      }

   }

   static class GzipDecompressingEntity extends HttpEntityWrapper {

      public GzipDecompressingEntity( final HttpEntity entity ) {
         super(entity);
      }

      @Override
      public InputStream getContent() throws IOException, IllegalStateException {

         // the wrapped entity's getContent() decides about repeatability
         InputStream wrappedin = wrappedEntity.getContent();

         return new GZIPInputStream(wrappedin);
      }

      @Override
      public long getContentLength() {
         // length of ungzipped content is not known
         return -1;
      }

   }

   static class IdleConnectionMonitorThread extends Thread {

      Logger                                _log = Logger.getLogger(IdleConnectionMonitorThread.class);

      private final ClientConnectionManager _conMan;
      private volatile boolean              _shutdown;


      public IdleConnectionMonitorThread( ClientConnectionManager connMgr ) {
         super(IdleConnectionMonitorThread.class.getSimpleName());
         setDaemon(true);
         this._conMan = connMgr;
      }

      @Override
      public void run() {
         try {
            _log.debug(getName() + " started");
            while ( !_shutdown ) {
               synchronized ( this ) {
                  TimeUnit.SECONDS.sleep(5);

                  // Close expired connections
                  _conMan.closeExpiredConnections();
                  // Optionally, close connections
                  // that have been idle longer than 30 sec
                  _conMan.closeIdleConnections(30, TimeUnit.SECONDS);
               }
            }
         }
         catch ( InterruptedException ex ) {
            _log.info(getName() + " interrupted. terminating");
         }
      }

      public void shutdown() {
         _shutdown = true;
         synchronized ( this ) {
            notifyAll();
         }
      }

   };

   static class TrustAllSslTrustManager implements X509TrustManager {

      public void checkClientTrusted( X509Certificate[] xcs, String string ) throws CertificateException {}

      public void checkServerTrusted( X509Certificate[] xcs, String string ) throws CertificateException {}

      public X509Certificate[] getAcceptedIssuers() {
         return null;
      }
   };

}
