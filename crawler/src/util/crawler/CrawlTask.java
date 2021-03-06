package util.crawler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import util.crawler.CrawlParams.SearchReplaceParam;
import util.crawler.Crawler.CrawlItem;
import util.crawler.proxy.Proxy;
import util.http.HttpClientFactory;
import util.string.StringTool;
import util.time.TimeUtils;
import util.xslt.Transformer;
import util.xslt.Transformer.TransformationError;
import util.xslt.Transformer.TransformationResult;


public class CrawlTask implements Runnable {

   private static final String  FOLLOWURL       = "$followurl$";
   private static final Pattern HREF            = Pattern.compile("(?i)<a .*?href=\"(.*?)\"[^>]*?(?:>(?s)(.{0,2500}?)</a|/>)");
   private static final Pattern BASE            = Pattern.compile("(?i)<base.+?href=\"(.*?)\".*?>");
   private static final Pattern LINEBREAKS      = Pattern.compile("(\\n|\\r\\n)");
   private static final Pattern SPACE           = Pattern.compile(" ");
   private static final Pattern PIPE            = Pattern.compile("\\|");
   private static final Pattern AMP_ENTITY      = Pattern.compile("&amp;");
   private static final Pattern XML_CHAR_ENTITY = Pattern.compile("&#(\\d+);");
   private static final Pattern PORT_EXTRACTOR  = Pattern.compile("^(.+):(\\d+)");

   private static final String FOLLOW_XPATHS_XSL =
         "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +                                                          //
               "<xsl:transform xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\r\n" +
               //
               "               xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\r\n" +
               //
               "               xmlns:f=\"http://localhost/functions\"\r\n" +
               //
               "               exclude-result-prefixes=\"xs f\"\r\n" +
               //
               "               version=\"2.0\">\r\n" +
               //
               "  \r\n" +
               //
               "  <xsl:output method=\"xml\" indent=\"yes\"/>\r\n" +
               //
               "  \r\n" +
               //
               "  <xsl:template match=\"/\">\r\n" +
               //
               "    @@@" +
               //
               "  </xsl:template>\r\n" +
               //
               "  \r\n" +
               //
               "</xsl:transform>";

   private static Logger _log = LoggerFactory.getLogger(CrawlTask.class);

   public static String applyPageReplacements( CrawlParams params, String page ) {
      if ( params == null ) {
         return page;
      }
      List<SearchReplaceParam> pageReplacements = params.getPageReplacements();
      if ( pageReplacements == null ) {
         return page;
      }

      for ( SearchReplaceParam sr : pageReplacements ) {
         Pattern searchPattern = sr.getSearchPattern();
         page = searchPattern.matcher(page).replaceAll(sr.getReplaceString());
      }
      return page;
   }

   /**
    * @return String[3], with String[0] null or host, String[1] normalized path and String[2] the scheme or null if none given
    */
   public static String[] makeAbsolute( CrawlItem parentCrawlItem, String pathDir, String path ) {
      String host = parentCrawlItem == null ? null : parentCrawlItem._host;
      if ( path.startsWith("/") ) {
         return new String[] { host, path, null };
      }
      if ( path.startsWith("http://") || path.startsWith("https://") ) {
         String scheme = path.substring(0, path.indexOf("://"));
         int hostNameStartIndex = path.indexOf("//") + 2;
         int firstSlashIndex = path.indexOf('/', hostNameStartIndex);
         if ( firstSlashIndex < 0 ) {
            return new String[] { path, "/", scheme };
         }
         return new String[] { path.substring(hostNameStartIndex, firstSlashIndex), path.substring(firstSlashIndex), scheme };
      }
      if ( path.startsWith("?") && parentCrawlItem != null ) {
         String name = parentCrawlItem._path.substring(pathDir.length() + 1);
         if ( name.indexOf('?') > 0 ) {
            name = name.substring(0, name.indexOf('?'));
         }
         path = name + path;
      }
      path = pathDir + '/' + path;

      StringBuilder sb = new StringBuilder();
      String[] s = StringTool.split(path, '/');
      for ( int i = s.length - 1; i > 0; i-- ) {
         if ( s[i].equals(".") ) {
            continue;
         } else if ( s[i].equals("..") ) {
            i--;
         } else {
            sb.insert(0, '/' + s[i]);
         }
      }
      if ( path.endsWith("/") ) {
         sb.append('/');
      }
      return new String[] { host, sb.toString(), null };
   }

   public static String normalize( String path ) {
      path = path.trim();
      path = SPACE.matcher(path).replaceAll("+");
      path = PIPE.matcher(path).replaceAll("%7C");
      path = AMP_ENTITY.matcher(path).replaceAll("&");
      return path;
   }

   private static String convertXmlCharEntitiesToUrl( String url ) {
      Matcher matcher = XML_CHAR_ENTITY.matcher(url);
      boolean result = matcher.find();
      if ( result ) {
         StringBuffer sb = new StringBuffer();
         do {
            int c = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(sb, "%" + Integer.toString(c, 16));
            result = matcher.find();
         }
         while ( result );
         matcher.appendTail(sb);
         return sb.toString();
      }
      return url;
   }

   private final Crawler _crawler;

   private final CrawlItem _crawlItem;

   private String _pathDir;

   private CrawlParams _params;

   int _index;

   public CrawlTask( Crawler crawler, CrawlItem crawlItem ) {
      _crawler = crawler;
      _crawlItem = crawlItem;
      _pathDir = _crawlItem._path.substring(0, _crawlItem._path.lastIndexOf('/'));
      _params = _crawler.getParams();
   }

   @Override
   public void run() {
      String url = _crawlItem._path;
      MDC.put("path", url);

      int maxRetries = getMaxRetries();
      while ( maxRetries-- > 0 ) {
         Proxy proxy = null;
         long t = System.currentTimeMillis();
         try {
            proxy = _crawler.checkoutProxy();
            HttpClient httpClient = proxy.getHttpClient();
            String urlHost = _crawlItem._host != null ? _crawlItem._host : _params.getHost();
            int port = -1;
            Matcher matcher = PORT_EXTRACTOR.matcher(urlHost);
            if ( matcher.find() ) {
               urlHost = matcher.group(1);
               port = Integer.parseInt(matcher.group(2));
            }
            HttpHost host = new HttpHost(urlHost, port, _crawlItem._scheme);
            HttpRequestBase request = new HttpGet(url);
            if ( url.endsWith(":POST") ) {
               request = createPost(url);
            }
            _crawler._params.applyAdditionalHeaders(request);
            String page = requestPage(httpClient, host, request);
            page = applyPageReplacements(_params, page);
            sanityCheck(page);
            proxy.addSuccessfulGet((int)(System.currentTimeMillis() - t));
            checkForHtmlBaseElement(page);
            int numberItemsAdded = transformAndAddResult(host, page);
            Set<CrawlItem> paths = extractCrawlItems(page);
            int numberCrawlItemsAdded = 0;
            for ( CrawlItem path : paths ) {
               boolean added = _crawler.addCrawlItem(path);
               if ( added ) {
                  numberCrawlItemsAdded++;
               }
            }

            String proxyString = numberCrawlItemsAdded == 0 && numberItemsAdded == 0 && _params.isUseProxies() ? ", " + proxy.getAddress().toString() : "";
            _log.debug("added " + numberCrawlItemsAdded + " URLs to queue, scraped " + numberItemsAdded + " items" + proxyString);
            _crawler._pathNumber++;
            if ( _crawler._pathNumber % 1000 == 0 ) {
               _log.info("got " + _crawler._pathNumber + " paths");
            }

            break; // we were successful
         }
         catch ( Exception argh ) {
            int requestTimeInMillis = (int)(System.currentTimeMillis() - t);
            proxy = handleException(argh, requestTimeInMillis, maxRetries, proxy);
         }
         finally {
            if ( proxy != null ) {
               _crawler.returnProxy(proxy);
            }
         }
      }
      _crawlItem.requestFinished();
      MDC.remove("path");
   }

   protected HttpResponse executeRequest( HttpClient httpClient, HttpHost host, HttpRequestBase get ) throws IOException {
      return httpClient.execute(host, get, _crawlItem._httpContext);
   }

   protected int getMaxRetries() {
      if ( _params.getMaxRetries() > 0 ) {
         return _params.getMaxRetries();
      }
      return _params._useProxies ? 20 : 1;
   }

   /**
    * @return the proxy parameter, or null, if the proxy is not to be used again 
    */
   protected Proxy handleException( Exception argh, int requestTimeInMillis, int numberOfRetriesLeft, Proxy proxy ) {
      if ( numberOfRetriesLeft > 0 ) {
         _log.info("Failed to get page (will retry)" + (_params.isUseProxies() ? " using proxy " + proxy : "") + ": " + argh);
         if ( !_params.isUseProxies() ) {
            _log.debug("waiting for 10 seconds before retrying");
            TimeUtils.sleepQuietlySeconds(10);
         }
      } else {
         _log.warn("Failed to get page", argh);
         if ( argh instanceof UnexceptedStatuscodeException ) {
            _crawlItem._errorStatusCode = ((UnexceptedStatuscodeException)argh)._statuscode;
         }
         _crawler._errorPaths.add(_crawlItem);
      }
      if ( proxy != null ) {
         proxy.addFaultyGet(requestTimeInMillis);
      }
      if ( argh instanceof ConnectException ) {
         if ( _params.isUseProxies() ) {
            _log.warn("forgetting proxy " + proxy);
         }
         proxy = null; // don't use proxy anymore
      } else if ( argh instanceof SocketException ) {
         // continue using proxy
      } else if ( argh instanceof IOException ) {
         if ( _params.isUseProxies() ) {
            _log.warn("forgetting proxy " + proxy);
         }
         proxy = null; // don't use proxy anymore
      }
      return proxy;
   }

   protected String readPage( HttpResponse response, HttpContext httpContext, String pageEncoding ) throws Exception {
      return HttpClientFactory.readPage(response, pageEncoding);
   }

   protected String requestPage( HttpClient httpClient, HttpHost host, HttpRequestBase get ) throws Exception {
      try {
         HttpResponse response = executeRequest(httpClient, host, get);
         if ( response.getStatusLine().getStatusCode() != 200 && response.getStatusLine().getStatusCode() != 404 ) {
            throw new UnexceptedStatuscodeException(response.getStatusLine().getStatusCode());
         }
         String page = readPage(response, _crawlItem._httpContext, _params.getForcedPageEncoding());
         return page;
      }
      catch ( Exception argh ) {
         get.abort(); // to return connection of httpClient back to the pool
         throw argh;
      }
   }

   private String applyFollowXPaths( String page ) {
      if ( _params.getFollowXPaths().isEmpty() ) {
         return page;
      }
      StringBuilder s = new StringBuilder();
      for ( String xpath : _params.getFollowXPaths() ) {
         s.append("<xsl:copy-of select=\"").append(xpath).append("\"/>");
      }
      String xslt = FOLLOW_XPATHS_XSL;
      xslt = xslt.replace("@@@", s);
      return applyXSLTToPage(xslt, page);
   }

   private String applyFollowXSLT( String page ) {
      String xslt = _params.getFollowXSLT();
      if ( StringUtils.isBlank(xslt) ) {
         return page;
      }
      return applyXSLTToPage(xslt, page);
   }

   private String applyXSLTToPage( String xslt, String page ) {
      TransformationResult transformationResult = Transformer.transform(page, xslt, _crawlItem._variablesForXSLT);
      if ( !transformationResult._errors.isEmpty() ) {
         StringBuilder s = new StringBuilder();
         for ( TransformationError e : transformationResult._errors ) {
            s.append("\n").append(e._error);
         }
         _log.warn("Transformation errors while applying XSLT to page: " + s);
      }
      return transformationResult._result;
   }

   /** search for a <base> tag and overwrite _pathDir with its value if present */
   private void checkForHtmlBaseElement( String page ) {
      Matcher matcher = BASE.matcher(page);
      if ( matcher.find() ) {
         String path = matcher.group(1);
         if ( path.isEmpty() ) {
            return;
         }
         int startIndex = 0;
         if ( path.contains("//") ) {
            startIndex = path.indexOf("//") + 2;
            if ( path.indexOf('/', startIndex) > 0 ) {
               startIndex = path.indexOf('/', startIndex);
            } else {
               _pathDir = "";
               return;
            }
         }
         _pathDir = path.substring(startIndex);
         if ( _pathDir.equalsIgnoreCase("/") ) {
            _pathDir = "";
         }
      }
   }

   private HttpRequestBase createPost( String url ) throws UnsupportedEncodingException {
      if ( url.endsWith(":POST") ) {
         url = url.substring(0, url.length() - 5);
      }
      List<String> params = new ArrayList<>();
      int indexOfQuestionmark = url.lastIndexOf('?');
      if ( indexOfQuestionmark > 0 ) {
         for ( String p : StringUtils.split(url.substring(indexOfQuestionmark + 1), '&') ) {
            String[] s = StringUtils.split(p, '=');
            if ( s.length == 0 ) {
               continue;
            }
            params.add(s[0]);
            if ( s.length > 1 ) {
               params.add(s[1]);
            } else {
               params.add("");
            }
         }
         url = url.substring(0, indexOfQuestionmark);
      }

      HttpPost post = new HttpPost(url);
      List<NameValuePair> formparams = new ArrayList<>();
      for ( int i = 0, length = params.size(); i < length; i += 2 ) {
         formparams.add(new BasicNameValuePair(params.get(i), params.get(i + 1)));
      }
      UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
      post.setEntity(entity);
      return post;
   }

   private Set<CrawlItem> extractCrawlItems( String page ) {
      Set<CrawlItem> paths = new HashSet<>();

      page = applyFollowXPaths(page);
      page = applyFollowXSLT(page);

      page = LINEBREAKS.matcher(page).replaceAll("");
      Matcher matcher = HREF.matcher(page);
      while ( matcher.find() ) {
         String path = new String(convertXmlCharEntitiesToUrl(matcher.group(1))); // new String(.) to get rid of memory leak because of substring not copying!
         String linklabel = new String(StringUtils.trimToEmpty(matcher.group(2))); // new String(.) to get rid of memory leak because of substring not copying!
         String[] normalizedPath = makeAbsolute(_crawlItem, _pathDir, normalize(path));
         for ( Pattern p : _params.getFollowPatterns() ) {
            if ( p.matcher(normalizedPath[1]).matches() && !isDontFollow(normalizedPath[1]) ) {
               paths.add(new CrawlItem(_params, _crawlItem, normalizedPath[0], normalizedPath[1], linklabel, getHttpContext(_crawlItem), normalizedPath[2]));
            }
         }
      }
      return paths;
   }

   private Map<String, String> followUrl( String key, String value ) {
      final Map<String, String>[] maps = new Map[1];
      Crawler crawlerDelegate = new Crawler(_params) {

         @Override
         public CrawlTask createCrawlTask( Crawler crawler, CrawlItem crawlItem ) {
            return _crawler.createCrawlTask(crawler, crawlItem);
         }

         @Override
         public CrawlParams getParams() {
            return _params;
         }

         @Override
         protected synchronized int addResult( Map<String, String>[] m ) {
            if ( m.length > 0 ) {
               maps[0] = m[0];
            }
            return 0;
         }

         @Override
         protected Proxy checkoutProxy() {
            return _crawler.checkoutProxy();
         }

         @Override
         protected void init() {
            // do nothing
         }

         @Override
         protected void returnProxy( Proxy p ) {
            _crawler.returnProxy(p);
         }

         @Override
         synchronized boolean addCrawlItem( CrawlItem crawlItem ) {
            // do nothing
            return false;
         }
      };

      String[] s = StringTool.split(value, '$');
      String url = s[s.length - 1].trim();
      String[] normalizedPath = makeAbsolute(_crawlItem, _pathDir, url);
      CrawlItem ci = new CrawlItem(_params, _crawlItem, normalizedPath[0], normalizedPath[1], null, getHttpContext(_crawlItem), normalizedPath[2]);

      for ( int i = 0, length = s.length - 1; i < length; i++ ) {
         if ( s[i].isEmpty() || s[i].equals("followurl") ) {
            continue;
         }
         String[] ss = StringTool.split(s[i], '=');
         if ( ss.length == 2 ) {
            ci.addVariableForXSLT(ss[0], ss[1]);
         }
      }

      String oldPath = MDC.get("path");
      crawlerDelegate.createCrawlTask(crawlerDelegate, ci).run();
      MDC.put("path", oldPath);

      if ( maps[0] != null ) {
         return maps[0];
      }

      return null;
   }

   private HttpContext getHttpContext( CrawlItem crawlItem ) {
      HttpContext httpContext = HttpClientFactory.createHttpContext(_params.isUseCookies());
      if ( crawlItem._httpContext != null && _params.isUseCookies() ) {
         httpContext.setAttribute(ClientContext.COOKIE_STORE, crawlItem._httpContext.getAttribute(ClientContext.COOKIE_STORE));
      }
      return httpContext;
   }

   private boolean isDontFollow( String path ) {
      for ( Pattern pp : _params.getDontFollowPatterns() ) {
         if ( pp.matcher(path).matches() ) {
            return true;
         }
      }
      return false;
   }

   private void sanityCheck( String page ) throws IOException {
      for ( Pattern p : _params.getSanePatterns() ) {
         if ( !p.matcher(page).find() ) {
            throw new IOException("insane result, " + p + " does not match" + (_params.isUseProxies() ? ", will forget this proxy" : ""));
         }
      }
      for ( Pattern p : _params.getInsanePatterns() ) {
         if ( p.matcher(page).find() ) {
            throw new IOException("insane result, " + p + " matches" + (_params.isUseProxies() ? ", will forget this proxy" : ""));
         }
      }
      for ( Pattern p : _params.getRetryPatterns() ) {
         if ( p.matcher(page).find() ) {
            throw new SocketException("suspicious result, " + p + " matches" + (_params.isUseProxies() ? ", will retry this proxy" : ""));
         }
      }
      if ( _params._useProxies ) {
         // the following two lines duplicate the effort of xslt processing in order to sanity check the result using the current proxy.
         String transformed = Transformer.transform(page, _params.getXslContents(), _crawlItem._variablesForXSLT)._result;
         try {
            Transformer.toMapLoud(transformed);
         }
         catch ( Exception argh ) {
            _log.debug("Exception in xslt step", argh);
            throw new SocketException("suspicious result, exception in xslt step" + (_params.isUseProxies() ? ", will retry this proxy" : ""));
         }
      }
   }

   private int transformAndAddResult( HttpHost host, String page ) {
      try {
         String transformed = Transformer.transform(page, _params.getXslContents(), _crawlItem._variablesForXSLT)._result;
         Map<String, String>[] maps = Transformer.toMap(transformed);
         for ( Map<String, String> map : maps ) {
            for ( Map.Entry<String, String> e : new HashMap<>(map).entrySet() ) {
               String key = e.getKey();
               String value = e.getValue();
               if ( value.contains(FOLLOWURL) ) {
                  Map<String, String> followUrlResults = followUrl(key, value);
                  if ( followUrlResults != null ) {
                     for ( Map.Entry<String, String> ee : followUrlResults.entrySet() ) {
                        if ( ee.getValue() != null && !ee.getValue().trim().isEmpty() ) {
                           map.put(ee.getKey(), ee.getValue());
                        }
                     }
                  }
               }
            }
         }
         for ( Map<String, String> map : maps ) {
            map.put(Crawler.RESULT_KEY_DEEPLINK, host + _crawlItem._path);
            map.put(Crawler.RESULT_KEY_CRAWLITEM_DEPTH, "" + _crawlItem._depth);
            map.put(Crawler.RESULT_KEY_ORIGINAL_PAGE, page);
         }
         return _crawler.addResult(maps);
      }
      catch ( Exception argh ) {
         _log.warn("failed to extract result from html page " + _crawlItem, argh);
         return 0;
      }
   }

   public static class UnexceptedStatuscodeException extends IOException {

      private final int _statuscode;

      public UnexceptedStatuscodeException( int statuscode ) {
         super("Unexpected status code - " + statuscode);
         _statuscode = statuscode;
      }

      public int getStatuscode() {
         return _statuscode;
      }
   }
}
