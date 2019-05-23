package util.string;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Filtert Strings, wenn sie nicht alle Tokens des aktuellen <code>filterStrings</code> enthalten.
 * Tokens, die mit '-' anfangen, dürfen NICHT enthalten sein. Default ist eine Substring-Suche, 
 * d.h. 'eng' findet auch 'englisch'. Für eine Wort- oder Phrasensuche das Wort in doppelte 
 * Anführungszeichen setzen.
 * Beispiel: \"eng\" findet nicht 'englisch', \"engl. Version\" findet nicht 'Version engl.'.
 * Für eine Oder-Suche die einzelnen Tokens mit '|' getrennt schreiben.
 * Beispiel: 'gebraucht|b-ware' findet sowohl 'gebrauchte Socken' als auch 'Socken B-ware'.
 * Vor oder-verknüpfte Substrings ein '-' zu schreiben liefert nichts Sinnvolles.
 */
public class StringFilter {

   protected Map<String, String[]> _orSearchCache = new HashMap<>();
   private String                  _filterString;
   private String[]                _filterTokens;


   public StringFilter() {}

   public StringFilter( String filterString ) {
      setFilterString(filterString);
   }

   /**
    * @return true, if the filter string does not match
    */
   public boolean filter( String element ) {
      return filter(element, false);
   }

   public boolean filter( String element, boolean isLowerCase ) {
      if ( !isLowerCase ) {
         element = element.toLowerCase();
      }
      for ( String filterToken : _filterTokens ) {
         if ( filterToken.length() > 0 && filterToken.charAt(0) == '-' ) {
            if ( filterToken.length() > 3 && filterToken.charAt(1) == '"' && filterToken.charAt(filterToken.length() - 1) == '"' ) { // wortsuche
               if ( containsWord(element, filterToken.substring(2, filterToken.length() - 1)) ) {
                  return true;
               }
            } else if ( filterToken.length() > 1 && element.contains(filterToken.substring(1)) ) {
               return true;
            }
         } else {
            if ( filterToken.length() > 2 && filterToken.charAt(0) == '"' && filterToken.charAt(filterToken.length() - 1) == '"'
               && filterToken.indexOf('|') < 0 ) { // wortsuche
               if ( !containsWord(element, filterToken.substring(1, filterToken.length() - 1)) ) {
                  return true;
               }
            } else if ( filterToken.length() > 2 && filterToken.indexOf("|") > 0 ) {
               boolean orSearch = orSearch(filterToken, element);
               if ( orSearch ) {
                  return true;
               }
            } else if ( !element.contains(filterToken) ) {
               return true;
            }
         }
      }
      return false;
   }

   public void setFilterString( String filterString ) {
      if ( filterString.equals(_filterString) ) {
         return;
      }

      _filterString = filterString.toLowerCase();
      _orSearchCache.clear();
      _filterTokens = coalescePhrases(_filterString);
   }

   protected String[] coalescePhrases( String filterString ) {
      List<String> filterTokens = new ArrayList<>();

      StringBuilder s = new StringBuilder();
      boolean openQuote = false;
      for ( int i = 0, length = filterString.length(); i < length; i++ ) {
         char c = filterString.charAt(i);
         if ( c == '"' ) {
            openQuote = !openQuote;
         }

         if ( c == ' ' && !openQuote ) {
            if ( s.length() > 0 ) {
               filterTokens.add(s.toString());
               s.setLength(0);
            }
         } else {
            s.append(c);
         }
      }
      filterTokens.add(s.toString());

      return filterTokens.toArray(new String[0]);
   }

   private boolean containsWord( String element, String word ) {
      int index = -1;
      int lastIndex = element.length() - 1;

      while ( (index = element.indexOf(word, index + 1)) >= 0 ) {
         if ( (index == 0 || !Character.isLetterOrDigit(element.charAt(index - 1))) // 
            && (index == lastIndex - word.length() + 1 || !Character.isLetterOrDigit(element.charAt(index + word.length()))) ) {
            return true;
         }
      }
      return false;
   }

   private boolean orSearch( String filterToken, String element ) {

      String[] s = _orSearchCache.get(filterToken);
      if ( s == null ) {
         s = StringTool.split(filterToken, '|');
         _orSearchCache.put(filterToken, s);
      }

      boolean atLeastOneToken = false;
      for ( String ss : s ) {
         if ( !atLeastOneToken && ss.length() > 0 ) {
            atLeastOneToken = true;
         }
         if ( ss.charAt(0) == '"' && ss.charAt(ss.length() - 1) == '"' ) {
            if ( containsWord(element, ss.substring(1, ss.length() - 1)) ) {
               return false;
            }
         } else if ( element.contains(ss) ) {
            return false;
         }
      }
      return atLeastOneToken;
   }
}
