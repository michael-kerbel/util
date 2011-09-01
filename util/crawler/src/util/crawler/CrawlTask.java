package util.crawler;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.protocol.HttpContext;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

import util.crawler.CrawlParams.SearchReplaceParam;
import util.crawler.Crawler.CrawlItem;
import util.crawler.proxy.Proxy;
import util.http.HttpClientFactory;
import util.string.StringTool;
import util.xslt.Transformer;
import util.xslt.Transformer.TransformationError;
import util.xslt.Transformer.TransformationResult;


public class CrawlTask implements Runnable {

   private static final String  FOLLOWURL         = "$followurl$";
   private static final Pattern HREF              = Pattern.compile("(?i)<a.+?href=\"(.*?)\".*?>(?s)(.*?)</a");
   private static final Pattern BASE              = Pattern.compile("(?i)<base.+?href=\"(.*?)\".*?>");
   private static final Pattern LINEBREAKS        = Pattern.compile("(\\n|\\r\\n)");
   private static final Pattern SPACE             = Pattern.compile(" ");
   private static final Pattern PIPE              = Pattern.compile("\\|");
   private static final Pattern AMP_ENTITY        = Pattern.compile("&amp;");
   private static final Pattern XML_CHAR_ENTITY   = Pattern.compile("&#(\\d+);");
   private static final String  FOLLOW_XPATHS_XSL = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" + // 
                                                     "<xsl:transform xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\r\n" + //
                                                     "               xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\r\n" + //
                                                     "               xmlns:f=\"http://localhost/functions\"\r\n" + //
                                                     "               exclude-result-prefixes=\"xs f\"\r\n" + //
                                                     "               version=\"2.0\">\r\n" + //
                                                     "  \r\n" + //
                                                     "  <xsl:output method=\"xml\" indent=\"yes\"/>\r\n" + //
                                                     "  \r\n" + //
                                                     "  <xsl:template match=\"/\">\r\n" + //
                                                     "    @@@" + //
                                                     "  </xsl:template>\r\n" + //
                                                     "  \r\n" + //
                                                     "</xsl:transform>";

   private static Logger        _log              = Logger.getLogger(CrawlTask.class);


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
    * @return String[2], with String[0] null or host and String[1] normalized path
    */
   public static String[] makeAbsolute( CrawlItem parentCrawlItem, String pathDir, String path ) {
      String host = parentCrawlItem == null ? null : parentCrawlItem._host;
      if ( path.startsWith("/") ) {
         return new String[] { host, path };
      }
      if ( path.startsWith("http://") ) {
         int firstSlashIndex = path.indexOf('/', 7);
         if ( firstSlashIndex < 0 ) {
            return new String[] { path, "/" };
         }
         return new String[] { path.substring(7, firstSlashIndex), path.substring(firstSlashIndex) };
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
      return new String[] { host, sb.toString() };
   }

   public static String normalize( String path ) {
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


   private final Crawler   _crawler;

   private final CrawlItem _crawlItem;

   private String          _pathDir;

   private CrawlParams     _params;


   public CrawlTask( Crawler crawler, CrawlItem crawlItem ) {
      _crawler = crawler;
      _crawlItem = crawlItem;
      _pathDir = _crawlItem._path.substring(0, _crawlItem._path.lastIndexOf('/'));
      _params = _crawler.getParams();
   }

   @Override
   public void run() {
      MDC.put("path", _crawlItem._path);

      int maxRetries = getMaxRetries();
      while ( maxRetries-- > 0 ) {
         Proxy proxy = null;
         long t = System.currentTimeMillis();
         try {
            proxy = _crawler.checkoutProxy();
            HttpClient httpClient = proxy.getHttpClient();
            HttpHost host = new HttpHost(_crawlItem._host != null ? _crawlItem._host : _params.getHost());
            HttpGet get = new HttpGet(_crawlItem._path);
            HttpResponse response = executeRequest(httpClient, host, get);
            if ( response.getStatusLine().getStatusCode() != 200 && response.getStatusLine().getStatusCode() != 404 ) {
               get.abort(); // to return connection of httpClient back to the pool
               throw new UnexceptedStatuscodeException(response.getStatusLine().getStatusCode());
            }
            String page = readPage(response, _crawlItem._httpContext);
            page = applyPageReplacements(_params, page);
            sanityCheck(page);
            checkForHtmlBaseElement(page);
            int numberItemsAdded = calcResult(host, page);
            //            _log.info(_path + "\n" + transformed);
            Set<CrawlItem> paths = extractCrawlItems(page);
            int numberCrawlItemsAdded = 0;
            for ( CrawlItem path : paths ) {
               boolean added = _crawler.addCrawlItem(path);
               if ( added ) {
                  numberCrawlItemsAdded++;
               }
            }
            proxy.addSuccessfulGet((int)(System.currentTimeMillis() - t));

            String proxyString = numberCrawlItemsAdded == 0 && numberItemsAdded == 0 && _params.isUseProxies() ? ", " + proxy.getAddress().toString() : "";
            _log.debug("added " + numberCrawlItemsAdded + " URLs to queue, scraped " + numberItemsAdded + " items" + proxyString);
            _crawler._pathNumber++;
            if ( _crawler._pathNumber % 1000 == 0 ) {
               _log.info("got " + _crawler._pathNumber + " paths");
            }

            break; // we were successful
         }
         catch ( Exception argh ) {
            if ( maxRetries > 0 ) {
               _log.info("Failed to get page (will retry)" + (_params.isUseProxies() ? " using proxy " + proxy : "") + ": " + argh);
            } else {
               _log.warn("Failed to get page", argh);
               if ( argh instanceof UnexceptedStatuscodeException ) {
                  _crawlItem._errorStatusCode = ((UnexceptedStatuscodeException)argh)._statuscode;
               }
               _crawler._errorPaths.add(_crawlItem);
            }
            if ( proxy != null ) {
               proxy.addFaultyGet((int)(System.currentTimeMillis() - t));
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
         }
         finally {
            if ( proxy != null ) {
               _crawler.returnProxy(proxy);
            }
         }
      }
      MDC.remove("path");
   }

   protected HttpResponse executeRequest( HttpClient httpClient, HttpHost host, HttpGet get ) throws IOException {
      return httpClient.execute(host, get, _crawlItem._httpContext);
   }

   protected int getMaxRetries() {
      if ( _params.getMaxRetries() > 0 ) {
         return _params.getMaxRetries();
      }
      return _params._useProxies ? 20 : 1;
   }

   protected String readPage( HttpResponse response, HttpContext httpContext ) throws Exception {
      return HttpClientFactory.readPage(response);
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

   private int calcResult( HttpHost host, String page ) {
      try {
         String transformed = Transformer.transform(page, _params.getXslContents(), _crawlItem._variablesForXSLT)._result;
         Map<String, String>[] maps = Transformer.toMap(transformed);
         for ( Map<String, String> map : maps ) {
            for ( Map.Entry<String, String> e : new HashMap<String, String>(map).entrySet() ) {
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

   private Set<CrawlItem> extractCrawlItems( String page ) {
      Set<CrawlItem> paths = new HashSet<CrawlItem>();

      page = applyFollowXPaths(page);
      page = applyFollowXSLT(page);

      page = LINEBREAKS.matcher(page).replaceAll("");
      Matcher matcher = HREF.matcher(page);
      while ( matcher.find() ) {
         String path = convertXmlCharEntitiesToUrl(matcher.group(1));
         String linklabel = new String(matcher.group(2)); // new String(.) to get rid of memory leak because of substring not copying!
         String[] normalizedPath = makeAbsolute(_crawlItem, _pathDir, normalize(path));
         for ( Pattern p : _params.getFollowPatterns() ) {
            if ( p.matcher(normalizedPath[1]).matches() && !isDontFollow(normalizedPath[1]) ) {
               paths.add(new CrawlItem(_crawlItem, normalizedPath[0], normalizedPath[1], linklabel, getHttpContext(_crawlItem)));
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
            if ( maps.length > 0 ) {
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
      CrawlItem ci = new CrawlItem(_crawlItem, normalizedPath[0], normalizedPath[1], null, getHttpContext(_crawlItem));

      for ( int i = 0, length = s.length - 1; i < length; i++ ) {
         if ( s[i].isEmpty() || s[i].equals("followurl") ) {
            continue;
         }
         String[] ss = StringTool.split(s[i], '=');
         if ( ss.length == 2 ) {
            ci.addVariableForXSLT(ss[0], ss[1]);
         }
      }

      String oldPath = (String)MDC.get("path");
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
