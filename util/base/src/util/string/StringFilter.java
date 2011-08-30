package util.string;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Filter Strings, wenn sie nicht alle Tokens des aktuellen <code>filterStrings</code> enthalten.
 * Tokens, die mit '-' anfangen, dürfen NICHT enthalten sein. Default ist eine Substring-Suche, 
 * d.h. 'eng' findet auch 'englisch'. Für eine Wort- oder Phrasensuche das Wort in doppelte 
 * Anführungszeichen setzen.
 * Beispiel: \"eng\" findet nicht 'englisch', \"engl. Version\" findet nicht 'Version engl.'.
 * Für eine Oder-Suche die einzelnen Tokens mit '|' getrennt schreiben.
 * Beispiel: 'gebraucht|b-ware' findet sowohl 'gebrauchte Socken' als auch 'Socken B-ware'.
 * Eine Oder-Suche ist nur als Substring-Suche möglich, nicht als Phrasensuche.
 * Vor oder-verknüpfte Substrings ein '-' zu schreiben liefert nichts Sinnvolles.
 */
public class StringFilter {

   protected Map<String, String[]> _orSearchCache = new HashMap<String, String[]>();
   private String                  _filterString;
   private String[]                _filterTokens;

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
            } else if ( filterToken.length() > 1 && element.indexOf(filterToken.substring(1)) >= 0 ) {
               return true;
            }
         } else {
            if ( filterToken.length() > 2 && filterToken.charAt(0) == '"' && filterToken.charAt(filterToken.length() - 1) == '"' ) { // wortsuche
               if ( !containsWord(element, filterToken.substring(1, filterToken.length() - 1)) ) {
                  return true;
               }
            } else if ( filterToken.length() > 2 && filterToken.indexOf("|") > 0 ) {
               boolean orSearch = orSearch(filterToken, element);
               if ( orSearch ) {
                  return true;
               }
            } else if ( element.indexOf(filterToken) < 0 ) {
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

      _filterString = filterString;
      _orSearchCache.clear();
      _filterTokens = coalescePhrases(StringTool.split(_filterString, ' '));
   }

   protected String[] coalescePhrases( String[] tokens ) {
      List<String> filterTokens = new ArrayList<String>(tokens.length);
      for ( int i = 0, length = tokens.length; i < length; i++ ) {
         if ( tokens[i].length() > 0 && (tokens[i].startsWith("-\"") || tokens[i].charAt(0) == '"') && tokens[i].charAt(tokens[i].length() - 1) != '"' ) {
            // phrasenende suchen
            int endPhraseIndex = -1;
            for ( int j = i + 1; j < length && endPhraseIndex < 0; j++ ) {
               if ( tokens[j].length() > 0 && tokens[j].charAt(tokens[j].length() - 1) == '"' ) {
                  endPhraseIndex = j;
               }
            }
            if ( endPhraseIndex > 0 ) {
               StringBuilder sb = new StringBuilder();
               for ( int j = i; j <= endPhraseIndex; j++ ) {
                  if ( j != i ) {
                     sb.append(" ");
                  }
                  sb.append(tokens[j]);
               }
               filterTokens.add(sb.toString());
               i = endPhraseIndex;
               continue;
            }
         }
         filterTokens.add(tokens[i]);
      }
      return filterTokens.toArray(new String[filterTokens.size()]);
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
         if ( element.indexOf(ss) >= 0 ) {
            return false;
         }
      }
      return atLeastOneToken;
   }
}
