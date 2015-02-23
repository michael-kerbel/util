package util.crawler.proxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ProxyList {

   private static Logger _log = LoggerFactory.getLogger(ProxyList.class);


   public static ProxyList getCurrentProxyList() {
      ProxyList proxyList = null;
      String proxyListFilename = System.getProperty("proxyListFilename", "proxies.txt");
      if ( proxyListFilename != null ) {
         proxyList = new ProxyList();
         FileInputStream in = null;
         try {
            in = new FileInputStream(proxyListFilename);
            proxyList.initFromDisk(in);
         }
         catch ( IOException argh ) {
            _log.warn("Failed to read proxy list from " + proxyListFilename, argh);
         }
         finally {
            IOUtils.closeQuietly(in);
         }
      }
      return proxyList;
   }

   public static void storeProxyList( ProxyList proxyList ) {
      try {
         FileUtils.writeStringToFile(new File(System.getProperty("proxyListFilename", "proxies.txt")), proxyList.toString());
      }
      catch ( IOException argh ) {
         _log.error("Failed to write to file", argh);
      }
   }


   List<ProxyAddress> _proxies         = new ArrayList<ProxyAddress>();

   long               _lastRefreshTime = System.currentTimeMillis();


   public ProxyList() {}

   public void addProxy( String p ) {
      try {
         _proxies.add(new ProxyAddress(p));
      }
      catch ( Exception argh ) {
         _log.warn("Failed to add proxy", argh);
         // ignore
      }
   }

   public long getLastRefreshTime() {
      return _lastRefreshTime;
   }

   public List<ProxyAddress> getProxies() {
      return _proxies;
   }

   public void initFromDisk() {
      InputStream in = null;
      try {
         in = getClass().getResourceAsStream("proxies.txt");
         initFromDisk(in);
      }
      catch ( IOException argh ) {
         _log.warn("Failed to init proxies", argh);
         throw new RuntimeException(argh);
      }
      finally {
         IOUtils.closeQuietly(in);
      }
   }

   public void initFromDisk( InputStream in ) throws IOException {
      List<String> proxies = IOUtils.readLines(in);
      for ( String p : proxies ) {
         if ( p.startsWith("lastRefreshTime=") ) {
            _lastRefreshTime = Long.parseLong(p.substring(16));
         } else {
            addProxy(p);
         }
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("lastRefreshTime=");
      sb.append(_lastRefreshTime);
      sb.append("\n");
      for ( ProxyAddress p : _proxies ) {
         sb.append(p.toString()).append("\n");
      }
      return sb.toString();
   }


   public static class ProxyAddress {

      String _ip;
      int    _port;


      public ProxyAddress( String p ) {
         int colonIndex = p.indexOf(':');
         if ( colonIndex <= 0 ) {
            throw new IllegalArgumentException("no port: " + p);
         }
         _ip = p.substring(0, colonIndex);
         _port = Integer.parseInt(p.substring(colonIndex + 1));
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
         ProxyAddress other = (ProxyAddress)obj;
         if ( _ip == null ) {
            if ( other._ip != null ) {
               return false;
            }
         } else if ( !_ip.equals(other._ip) ) {
            return false;
         }
         if ( _port != other._port ) {
            return false;
         }
         return true;
      }

      @Override
      public int hashCode() {
         final int prime = 31;
         int result = 1;
         result = prime * result + ((_ip == null) ? 0 : _ip.hashCode());
         result = prime * result + _port;
         return result;
      }

      @Override
      public String toString() {
         return _ip + ":" + _port;
      }
   }

}
