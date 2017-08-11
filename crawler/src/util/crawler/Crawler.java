package util.crawler;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpHost;
import org.apache.http.client.CookieStore;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import util.concurrent.ExecutorUtils;
import util.crawler.proxy.Proxy;
import util.crawler.proxy.ProxyCrawler;
import util.crawler.proxy.ProxyList;
import util.crawler.proxy.ProxyList.ProxyAddress;
import util.crawler.proxy.ProxyPool;
import util.http.HttpClientFactory;


public class Crawler {

   public static final String RESULT_KEY_CRAWLITEM_DEPTH = "crawlItem.depth";
   public static final String RESULT_KEY_DEEPLINK        = "deeplink";
   public static final String RESULT_KEY_ORIGINAL_PAGE   = "originalPage";

   private static final Pattern PATTERN_RE_ENCODE_URL = Pattern.compile("([/?&=]*)([^/?&=]+)");

   private static Logger _log = LoggerFactory.getLogger(Crawler.class);


   public static void main( String[] args ) {
      main(args, new CrawlerFactory() {

         @Override
         public Crawler newCrawler( CrawlParams params ) {
            return new Crawler(params);
         }
      });
   }

   public static void main( String[] args, CrawlerFactory factory ) {
      if ( args.length == 0 || !new File(args[0]).exists() ) {
         _log.error("please provide a valid config file as first parameter");
         System.exit(-1);
      }

      FileSystemXmlApplicationContext context = new FileSystemXmlApplicationContext(args[0]);
      Map<String, CrawlParams> beans = context.getBeansOfType(CrawlParams.class);
      for ( Map.Entry<String, CrawlParams> e : beans.entrySet() ) {
         String beanname = e.getKey();
         CrawlParams params = e.getValue();
         params.setParent(new File(args[0]).getParentFile());

         _log.info("starting crawl for '" + beanname + "'");
         factory.newCrawler(params).crawl();
         _log.info("finished crawl for '" + beanname + "'");
      }
      context.close();

      System.exit(0);
   }

   public static String reEncodeUrlPath( String path ) {
      try {
         Matcher matcher = PATTERN_RE_ENCODE_URL.matcher(path);

         boolean result = matcher.find();
         if ( result ) {
            StringBuffer sb = new StringBuffer();
            do {
               String reEncodedFragment = URLEncoder.encode(URLDecoder.decode(matcher.group(2), "UTF-8"), "UTF-8");
               matcher.appendReplacement(sb, matcher.group(1) + reEncodedFragment);
               result = matcher.find();
            }
            while ( result );
            matcher.appendTail(sb);
            return sb.toString();
         }
      }
      catch ( UnsupportedEncodingException argh ) {
         // ignored, as this cannot happen in any JVM
      }
      return path;
   }


   protected CrawlParams        _params;
   protected ThreadPoolExecutor _executor;
   protected Set<CrawlItem>     _crawlItems = new LinkedHashSet<CrawlItem>();
   protected Proxy              _proxy;
   protected ProxyPool          _proxyPool;
   protected List<CrawlItem>    _errorPaths = new ArrayList<CrawlItem>();
   protected int                _pathNumber = 0;

   protected AtomicInteger _crawlTaskIndex = new AtomicInteger(0);


   public Crawler( CrawlParams params ) {
      _params = params;
      init();
   }

   public CrawlParams getParams() {
      return _params;
   }

   public void run() {
      crawl();
   }

   public void stop() {
      synchronized ( _executor ) {
         _executor.shutdownNow();
      }
   }

   protected synchronized int addResult( Map<String, String>[] maps ) {
      return 0;
   }

   protected Proxy checkoutProxy() {
      if ( _proxyPool != null ) {
         synchronized ( _proxyPool ) {
            if ( _proxyPool.size() == 0 ) {
               _log.warn("got no proxies left, re-initializing proxy pool!");
               initProxyPool();
            }
            Proxy proxy = _proxyPool.checkoutProxy();
            if ( proxy == null ) {
               _log.warn("got no proxies left, re-initializing proxy pool!");
               initProxyPool();
               proxy = _proxyPool.checkoutProxy();
            }
            return proxy;
         }
      }
      return _proxy;
   }

   protected void crawl() {
      addStartURLs();

      while ( true ) {
         boolean check = false;
         synchronized ( _executor ) {
            if ( _executor.isShutdown() ) {
               throw new RuntimeException("Crawler was stopped early.");
            }
            check = _executor.getQueue().size() > 0;
            check |= _executor.getCompletedTaskCount() < _crawlItems.size();
         }
         if ( !check ) {
            break;
         }
         sleep(1000);
      }

      _log.info("done...");

      synchronized ( _executor ) {
         _executor.shutdown();
         try {
            _executor.awaitTermination(10, TimeUnit.SECONDS);
         }
         catch ( InterruptedException argh ) {
            // ignore
         }
      }

      for ( CrawlItem errorPath : _errorPaths ) {
         _log.error("Failed to get " + errorPath);
      }

      if ( _proxyPool != null ) {
         _log.info("\n" + _proxyPool);
      }
   }

   protected CrawlTask createCrawlTask( Crawler crawler, CrawlItem crawlItem ) {
      return new CrawlTask(crawler, crawlItem);
   }

   protected ThreadPoolExecutor createCrawlTaskExecutor() {
      int numberOfThreads = _params.getNumberOfThreads();
      if ( _params.isLIFO() ) {
         BlockingQueue<Runnable> queue = new PriorityBlockingQueue<Runnable>(11, new Comparator<Runnable>() {

            @Override
            public int compare( Runnable o1, Runnable o2 ) {
               CrawlTask c1 = (CrawlTask)o1;
               CrawlTask c2 = (CrawlTask)o2;
               return -(c1._index < c2._index ? -1 : (c1._index == c2._index ? 0 : 1));
            }
         });
         return new ThreadPoolExecutor(numberOfThreads, numberOfThreads, 0L, TimeUnit.MILLISECONDS, queue,
            new ExecutorUtils.NamedThreadFactory("crawl task executor - " + _params.getId() + " "));
      } else {
         return ExecutorUtils.newFixedThreadPool(numberOfThreads, "crawl task executor - " + _params.getId() + " ");
      }
   }

   protected void init() {
      if ( _params.isUseProxies() ) {
         initProxyPool();
      } else {
         _proxy = new Proxy(new ProxyAddress("127.0.0.1:80"));
         HttpClientFactory httpClientFactory = new HttpClientFactory();
         httpClientFactory.setUserAgent(_params.getUserAgent());
         httpClientFactory.setConnectionTimeout(_params.getConnectionTimeout());
         httpClientFactory.setSoTimeout(_params.getSocketTimeout());
         httpClientFactory.setUseCookies(_params.isUseCookies());
         if ( _params.getAuthenticationUser() != null ) {
            httpClientFactory.setUser(_params.getAuthenticationUser());
            httpClientFactory.setPassword(_params.getAuthenticationPassword());
         }
         _proxy.setHttpClient(httpClientFactory.create());
      }

      _executor = createCrawlTaskExecutor();
   }

   protected void initProxyPool() {
      ProxyList proxyList = ProxyList.getCurrentProxyList();
      if ( proxyList == null ) {
         synchronized ( Crawler.class ) {
            proxyList = ProxyList.getCurrentProxyList();
            if ( proxyList == null ) {
               ProxyCrawler proxyCrawler = new ProxyCrawler();
               proxyCrawler.crawl();
               proxyList = proxyCrawler.getProxyList();
            }
         }
      }
      HttpHost latencyTestHost = new HttpHost(_params.getHost());
      _proxyPool = new ProxyPool(proxyList, latencyTestHost, _params.getSanePatterns(), _params.getInsanePatterns(), _params.getUserAgent(),
         _params.getAuthenticationUser(), _params.getAuthenticationPassword(), _params.getSocketTimeout(), _params.getConnectionTimeout(), "http");
      _log.info("using proxy pool with " + _proxyPool.size() + " proxies");
   }

   protected void returnProxy( Proxy p ) {
      if ( _proxyPool != null ) {
         _proxyPool.returnProxy(p);
      }
   }

   synchronized boolean addCrawlItem( CrawlItem crawlItem ) {
      if ( !_crawlItems.contains(crawlItem) ) {
         //         _log.debug("adding " + crawlItem);
         _crawlItems.add(crawlItem);
         if ( _crawlItems.size() % 1000 == 0 ) {
            _log.info("added " + _crawlItems.size() + " to queue");
         }
         try {
            synchronized ( _executor ) {
               CrawlTask crawlTask = createCrawlTask(this, crawlItem);
               crawlTask._index = _crawlTaskIndex.getAndIncrement();
               _executor.execute(crawlTask);
            }
         }
         catch ( RejectedExecutionException e ) {
            return false;
         }
         return true;
      }
      return false;
   }

   private void addStartURLs() {
      HttpContext httpContext = HttpClientFactory.createHttpContext(_params.isUseCookies());

      List<String> startURLs = _params.getStartURLs();
      if ( _params.isLIFO() ) {
         /* We have to do some voodoo, to make LIFO work: 
          * We reverse the order of the startURLs, since it is unnatural, to put the first URLs to the end.
          * But as soon as we add the first of our startURLs, it is executed. Not really LIFO. That's why we add the first URLs (un-reversed) first, 
          * as long as there is a Thread available in our Threadpool.  
          */
         List<String> reversedStartURLs = new ArrayList<String>();

         for ( int i = 0, length = Math.min(_params.getNumberOfThreads(), startURLs.size()); i < length; i++ ) {
            addCrawlItem(createCrawlItem(httpContext, startURLs.get(i)));
         }
         for ( int i = startURLs.size() - 1; i >= 0; i-- ) {
            reversedStartURLs.add(startURLs.get(i));
         }
         startURLs = reversedStartURLs;
      }

      for ( String startPath : startURLs ) {
         addCrawlItem(createCrawlItem(httpContext, startPath));
      }
   }

   private CrawlItem createCrawlItem( HttpContext httpContext, String url ) {
      String[] normalizedPath = CrawlTask.makeAbsolute(null, null, url);
      CrawlItem crawlItem = new CrawlItem(_params, null, normalizedPath[0], normalizedPath[1], null, httpContext, normalizedPath[2]);
      return crawlItem;
   }

   private void sleep( long ms ) {
      try {
         Thread.sleep(ms);
      }
      catch ( InterruptedException argh ) {
         // ignore
      }
   }


   public static class CrawlItem {

      String                  _host             = null;
      String                  _path;
      String                  _linklabel;
      int                     _depth            = 0;
      CrawlItem               _parent;
      HashMap<String, String> _variablesForXSLT = new HashMap<String, String>();
      HttpContext             _httpContext;
      int                     _errorStatusCode  = 0;
      String                  _scheme           = "http";


      public CrawlItem( CrawlParams params, CrawlItem parent, String host, String path, String linklabel, HttpContext httpContext, String scheme ) {
         if ( host != null && !host.equalsIgnoreCase(params.getHost()) ) {
            _host = host;
         }
         _path = path;
         if ( params.isReEncodeUrls() ) {
            _path = reEncodeUrlPath(_path);
         }
         _linklabel = linklabel;
         _parent = parent;
         _httpContext = httpContext;
         _variablesForXSLT.put("url", path);
         _variablesForXSLT.put("linklabel", linklabel);
         _scheme = scheme == null ? (_parent != null ? _parent._scheme : _scheme) : scheme;
         if ( _parent != null ) {
            _depth = _parent._depth + 1;
         }
      }

      public void addVariableForXSLT( String variableName, String value ) {
         _variablesForXSLT.put(variableName, value);
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
         CrawlItem other = (CrawlItem)obj;
         if ( _host == null ) {
            if ( other._host != null ) {
               return false;
            }
         } else if ( !_host.equals(other._host) ) {
            return false;
         }
         if ( _path == null ) {
            if ( other._path != null ) {
               return false;
            }
         } else if ( !_path.equals(other._path) ) {
            return false;
         }
         return true;
      }

      public int getErrorStatusCode() {
         return _errorStatusCode;
      }

      @Override
      public int hashCode() {
         final int prime = 31;
         int result = 1;
         result = prime * result + ((_host == null) ? 0 : _host.hashCode());
         result = prime * result + ((_path == null) ? 0 : _path.hashCode());
         return result;
      }

      public void requestFinished() {
         if ( _httpContext != null ) {
            // we don't want to keep all historic httpContexts and their data structures in memory,
            // so we replace it with something lean
            _httpContext = new CookieOnlyHttpContext(_httpContext);
         }
      }

      @Override
      public String toString() {
         return (_host == null ? "" : _host) + _path;
      }
   }


   public static abstract class CrawlerFactory {

      public abstract Crawler newCrawler( CrawlParams params );
   }


   private static class CookieOnlyHttpContext implements HttpContext {

      private CookieStore _cookieStore;


      public CookieOnlyHttpContext( HttpContext httpContext ) {
         _cookieStore = (CookieStore)httpContext.getAttribute(ClientContext.COOKIE_STORE);
      }

      @Override
      public Object getAttribute( String id ) {
         if ( ClientContext.COOKIE_STORE.equals(id) ) {
            return _cookieStore;
         }
         return null;
      }

      @Override
      public Object removeAttribute( String id ) {
         if ( ClientContext.COOKIE_STORE.equals(id) ) {
            Object cookieStore = _cookieStore;
            _cookieStore = null;
            return cookieStore;
         }
         return null;
      }

      @Override
      public void setAttribute( String id, Object obj ) {
         if ( ClientContext.COOKIE_STORE.equals(id) ) {
            _cookieStore = (CookieStore)obj;
         }
      }

   }

}
