package util.swt.editor;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;


public class KeywordManager {

   private static Logger                            _log  = Logger.getLogger(KeywordManager.class);

   // cache of Template objects
   private static Map<String, Map<String, Keyword>> CACHE = new Hashtable<String, Map<String, Keyword>>();


   public static synchronized Map<String, Keyword> getKeywords( String extension ) {
      // Check in cache
      Map<String, Keyword> keywords = CACHE.get(extension);
      if ( keywords == null ) {
         // Not in cache; load it and put in cache
         keywords = loadKeywords(extension);
         if ( keywords != null ) CACHE.put(extension, keywords);
      }
      return keywords;
   }

   private static String getValue( ResourceBundle rb, String key ) {
      try {
         return rb.getString(key);
      }
      catch ( MissingResourceException argh ) {
         return null;
      }
   }

   private static Map<String, Keyword> loadKeywords( String extension ) {
      Map<String, Keyword> keywords = new HashMap<String, Keyword>();
      try {
         ResourceBundle rb = ResourceBundle.getBundle(KeywordManager.class.getPackage().getName() + "." + extension + "-keywords");
         for ( Enumeration<String> iterator = rb.getKeys(); iterator.hasMoreElements(); ) {
            String key = iterator.nextElement();
            String value = getValue(rb, key);
            keywords.put(key, new Keyword(key, value != null ? value : ""));
         }
      }
      catch ( MissingResourceException e ) {
         // Ignore
         _log.warn(e.getMessage());
      }
      return keywords;
   }
}