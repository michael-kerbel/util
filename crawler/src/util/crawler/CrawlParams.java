package util.crawler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpRequestBase;

import util.crawler.proxy.Proxy;
import util.http.HttpClientFactory;


public class CrawlParams {

   int                      _numberOfThreads    = 3;
   int                      _socketTimeout      = HttpClientFactory.DEFAULT_VALUE_SOCKET_TIMEOUT;
   int                      _connectionTimeout  = HttpClientFactory.DEFAULT_VALUE_CONNECTION_TIMEOUT;
   int                      _maxRetries         = -1;
   String                   _userAgent          = HttpClientFactory.DEFAULT_VALUE_USER_AGENT;
   private String           _id;
   String                   _host;
   String                   _authenticationUser;
   String                   _authenticationPassword;
   String                   _forcedPageEncoding;
   String                   _xsl;
   String                   _followXSLT;
   List<String>             _startURLs;
   List<String>             _dontFollowRegexes;
   List<String>             _followRegexes;
   List<String>             _saneRegexes;
   List<String>             _retryRegexes;
   List<String>             _insaneRegexes;
   List<SearchReplaceParam> _pageReplacements;
   List<String>             _followXPaths       = Collections.emptyList();
   List<Pattern>            _followPatterns     = Collections.emptyList();
   List<Pattern>            _dontFollowPatterns = Collections.emptyList();
   List<Pattern>            _sanePatterns       = Collections.emptyList();
   List<Pattern>            _retryPatterns      = Collections.emptyList();
   List<Pattern>            _insanePatterns     = Collections.emptyList();
   File                     _parentFile;
   boolean                  _useProxies         = false;
   boolean                  _useCookies         = false;
   boolean                  _LIFO               = false;
   boolean                  _reEncodeUrls       = false;
   List<String>             _additionalHeaders;
   Comparator<Proxy>        _proxyComparator;

   transient String _xslContents;


   public void applyAdditionalHeaders( HttpRequestBase req ) {
      if ( _additionalHeaders == null ) {
         return;
      }
      for ( int i = 0, length = _additionalHeaders.size(); i < length; i += 2 ) {
         req.setHeader(_additionalHeaders.get(i), _additionalHeaders.get(i + 1));
      }
   }

   public List<String> getAdditionalHeaders() {
      return _additionalHeaders;
   }

   public String getAuthenticationPassword() {
      return _authenticationPassword;
   }

   public String getAuthenticationUser() {
      return _authenticationUser;
   }

   public int getConnectionTimeout() {
      return _connectionTimeout;
   }

   public List<Pattern> getDontFollowPatterns() {
      return _dontFollowPatterns;
   }

   public List<String> getDontFollowRegexes() {
      return _dontFollowRegexes;
   }

   public List<Pattern> getFollowPatterns() {
      return _followPatterns;
   }

   public List<String> getFollowRegexes() {
      return _followRegexes;
   }

   public List<String> getFollowXPaths() {
      return _followXPaths;
   }

   public String getFollowXSLT() {
      return _followXSLT;
   }

   public String getForcedPageEncoding() {
      return _forcedPageEncoding;
   }

   public String getHost() {
      return _host;
   }

   public String getId() {
      return _id;
   }

   public List<Pattern> getInsanePatterns() {
      return _insanePatterns;
   }

   public List<String> getInsaneRegexes() {
      return _insaneRegexes;
   }

   public int getMaxRetries() {
      return _maxRetries;
   }

   public int getNumberOfThreads() {
      return _numberOfThreads;
   }

   public List<SearchReplaceParam> getPageReplacements() {
      return _pageReplacements;
   }

   public Comparator<Proxy> getProxyComparator() {
      return _proxyComparator;
   }

   public List<Pattern> getRetryPatterns() {
      return _retryPatterns;
   }

   public List<String> getRetryRegexes() {
      return _retryRegexes;
   }

   public List<Pattern> getSanePatterns() {
      return _sanePatterns;
   }

   public List<String> getSaneRegexes() {
      return _saneRegexes;
   }

   public int getSocketTimeout() {
      return _socketTimeout;
   }

   public List<String> getStartURLs() {
      return _startURLs;
   }

   public String getUserAgent() {
      return _userAgent;
   }

   public String getXsl() {
      return _xsl;
   }

   public String getXslContents() {
      if ( _xslContents == null ) {
         try {
            File xsltFile = new File(_parentFile, _xsl);
            _xslContents = FileUtils.readFileToString(xsltFile);
         }
         catch ( IOException argh ) {
            throw new RuntimeException("Failed to read xslt file.", argh);
         }
      }
      return _xslContents;
   }

   public boolean isLIFO() {
      return _LIFO;
   }

   public boolean isReEncodeUrls() {
      return _reEncodeUrls;
   }

   public boolean isUseCookies() {
      return _useCookies;
   }

   public boolean isUseProxies() {
      return _useProxies;
   }

   public void setAdditionalHeaders( List<String> additionalHeaders ) {
      if ( additionalHeaders.size() % 2 != 0 ) {
         throw new RuntimeException("Headers must be defined in tupels, first a string with the headername, then the value.");
      }
      _additionalHeaders = additionalHeaders;
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

   public void setDontFollowRegexes( List<String> dontFollowRegexes ) {
      _dontFollowRegexes = dontFollowRegexes;
      _dontFollowPatterns = new ArrayList<Pattern>(_dontFollowRegexes.size());
      for ( String r : dontFollowRegexes ) {
         _dontFollowPatterns.add(Pattern.compile(r));
      }
   }

   public void setFollowRegexes( List<String> followRegexes ) {
      _followRegexes = followRegexes;
      _followPatterns = new ArrayList<Pattern>(_followRegexes.size());
      for ( String r : followRegexes ) {
         _followPatterns.add(Pattern.compile(r));
      }
   }

   public void setFollowXPaths( List<String> followXPaths ) {
      _followXPaths = followXPaths;
   }

   public void setFollowXSLT( String followXSLT ) {
      _followXSLT = StringUtils.trimToNull(followXSLT);
   }

   public void setForcedPageEncoding( String forcedPageEncoding ) {
      _forcedPageEncoding = forcedPageEncoding;
   }

   public void setHost( String host ) {
      _host = host;
      if ( host != null && host.startsWith("http") ) {
         throw new IllegalArgumentException("host must not contain the protocol (http)! " + host);
      }
   }

   public void setId( String id ) {
      _id = id;
   }

   public void setInsaneRegexes( List<String> insaneRegexes ) {
      _insaneRegexes = insaneRegexes;
      _insanePatterns = new ArrayList<Pattern>(_insaneRegexes.size());
      for ( String r : insaneRegexes ) {
         _insanePatterns.add(Pattern.compile(r));
      }
   }

   public void setLIFO( boolean lIFO ) {
      _LIFO = lIFO;
   }

   public void setMaxRetries( int maxRetries ) {
      _maxRetries = maxRetries;
   }

   public void setNumberOfThreads( int numberOfThreads ) {
      _numberOfThreads = numberOfThreads;
   }

   public void setPageReplacements( List<SearchReplaceParam> pageReplacements ) {
      _pageReplacements = pageReplacements;
   }

   public void setParent( File parentFile ) {
      _parentFile = parentFile;
      if ( !new File(_parentFile, _xsl).exists() ) {
         throw new RuntimeException("xsl " + new File(_parentFile, _xsl) + " does not exist");
      }
   }

   public void setProxyComparator( Comparator<Proxy> proxyComparator ) {
      _proxyComparator = proxyComparator;
   }

   public void setReEncodeUrls( boolean reEncodeUrls ) {
      _reEncodeUrls = reEncodeUrls;
   }

   public void setRetryRegexes( List<String> retryRegexes ) {
      _retryRegexes = retryRegexes;
      _retryPatterns = new ArrayList<Pattern>(_retryRegexes.size());
      for ( String r : retryRegexes ) {
         _retryPatterns.add(Pattern.compile(r));
      }
   }

   public void setSaneRegexes( List<String> saneRegexes ) {
      _saneRegexes = saneRegexes;
      _sanePatterns = new ArrayList<Pattern>(_saneRegexes.size());
      for ( String r : saneRegexes ) {
         _sanePatterns.add(Pattern.compile(r));
      }
   }

   public void setSocketTimeout( int socketTimeout ) {
      _socketTimeout = socketTimeout;
   }

   public void setStartURLs( List<String> startURLs ) {
      _startURLs = startURLs;
   }

   public void setUseCookies( boolean useCookies ) {
      _useCookies = useCookies;
   }

   public void setUseProxies( boolean useProxies ) {
      _useProxies = useProxies;
   }

   public void setUserAgent( String userAgent ) {
      _userAgent = userAgent;
   }

   public void setXsl( String xsl ) {
      _xsl = xsl;
   }


   public static class SearchReplaceParam {

      private Pattern _search;
      private String  _replace;


      public String getReplaceString() {
         return _replace;
      }

      public Pattern getSearchPattern() {
         return _search;
      }

      public void setReplaceString( String replace ) {
         _replace = replace;
      }

      public void setSearchRegex( String search ) {
         _search = Pattern.compile(search);
      }

   }
}