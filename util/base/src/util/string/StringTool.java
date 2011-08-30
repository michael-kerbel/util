package util.string;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.WeakHashMap;


public class StringTool {

   public static final char          NEW_LINE           = '\n';
   public static final char          SEPARATOR          = '\t';
   public static final char          ESCAPE_CHAR        = '\\';
   public static final char          ESCAPE_NEW_LINE    = 'n';
   public static final char          ESCAPE_SEPARATOR   = 't';

   private static Map                _uniqueStrings     = new WeakHashMap();

   private static boolean            _cachingConfigured = false;

   private static boolean            _cachingDisabled;

   private static ThreadLocal<int[]> _d                 = new ThreadLocal<int[]>() {

                                                           @Override
                                                           protected int[] initialValue() {
                                                              return new int[100];
                                                           }
                                                        };
   private static ThreadLocal<int[]> _p                 = new ThreadLocal<int[]>() {

                                                           @Override
                                                           protected int[] initialValue() {
                                                              return new int[100];
                                                           }
                                                        };


   /**
    * Konvertiert ein <code>byte[]</code> in ein <code>char[]</code> unter Nicht-Beachtung der Vorzeichen.<p>
    * Aus <code>0x80</code>, was als <code>byte</code> eine <code>-127</code> darstellt, wird <code>0x80</code>,
    * was als <code>char</code> eine <code>128</code> ist.<p>
    * Nützlich, wenn aus einem <code>InputStream</code> oder <code>Reader</code> ein bitgleicher String gemacht
    * werden soll.
    * @param bytes ein beliebiges <code>byte[]</code>
    * @return ein bitgleiches <code>char[]</code>
    */
   public static final char[] bytesToChars( byte[] bytes ) {

      char[] chars = new char[bytes.length];
      for ( int i = 0; i < bytes.length; i++ ) {
         if ( bytes[i] < 0 ) {
            chars[i] = (char)(256 + bytes[i]);
         } else {
            chars[i] = (char)bytes[i];
         }
      }
      return chars;
   }

   public static final String concat( long[] fields, String sep ) {
      StringBuilder buf = new StringBuilder();
      boolean first = true;
      for ( long s : fields ) {
         if ( !first ) {
            buf.append(sep);
         }
         buf.append(s);
         first = false;
      }
      return buf.toString();
   }

   public static final String concat( String[] fields, String sep ) {
      StringBuilder buf = new StringBuilder();
      boolean first = true;
      for ( String s : fields ) {
         if ( !first ) {
            buf.append(sep);
         }
         buf.append(s);
         first = false;
      }
      return buf.toString();
   }

   /** @return true for sep=' ' and token="xyz" and s in ("xyz", "3122 xyz", "4232 xyz 423") and false for s in ("xyzz", "xxyz", "1 xxyz 3") */
   public static final boolean contains( String s, char sep, String token ) {
      int i = -1;
      int tl = token.length();
      do {
         i = s.indexOf(token, i + 1);
         int iEnd = i == -1 ? -1 : s.indexOf(sep, i + tl);
         int iBegin = i <= 0 ? -1 : (s.charAt(i - 1) == sep ? i : i - 1);
         int l = (iEnd == -1 ? s.length() : iEnd) - (iBegin == -1 ? 0 : iBegin);
         if ( i >= 0 && l == tl ) {
            return true;
         }
      }
      while ( i >= 0 );
      return false;
   }

   public static String escape( String str ) {
      if ( str == null ) {
         return null;
      }
      StringBuffer sb = new StringBuffer(str.length() + 10);
      char c;
      int len = str.length();
      for ( int i = 0; i < len; i++ ) {
         c = str.charAt(i);
         switch ( c ) {
         case ESCAPE_CHAR:
            sb.append(ESCAPE_CHAR).append(ESCAPE_CHAR);
            break;
         case SEPARATOR:
            sb.append(ESCAPE_CHAR).append(ESCAPE_SEPARATOR);
            break;
         case NEW_LINE:
            sb.append(ESCAPE_CHAR).append(ESCAPE_NEW_LINE);
            break;
         default:
            sb.append(c);
            break;
         }
      }
      return sb.toString();
   }

   public static int getUniqueStringsMemoryConsumption() {
      int mem = 0;
      synchronized ( _uniqueStrings ) {
         for ( Iterator iter = _uniqueStrings.keySet().iterator(); iter.hasNext(); ) {
            String s = (String)iter.next();
            if ( s != null ) {
               mem += s.length() << 1;
            }
         }
      }
      return mem;
   }

   /**
    * Rückt <code>string</code> ein. Fügt also am Anfang jeder Zeile <code>indentString</code> ein.
    * @param string zu bearbeitender <code>String</code>
    * @param indentString Am Anfang jeder Zeile einzufügender <code>String</code>
    * @return eingerückter <code>String</code>
    */
   public static final StringBuffer indent( String string, String indentString ) {
      return indent(new StringBuffer(string), indentString);
   }

   /**
    * Rückt <code>string</code> ein. Fügt also am Anfang jeder Zeile <code>indentString</code> ein.
    * @param string zu bearbeitender <code>StringBuffer</code>
    * @param indentString Am Anfang jeder Zeile einzufügender <code>String</code>
    * @return eingerückter <code>String</code>
    */
   public static final StringBuffer indent( StringBuffer string, String indentString ) {

      string.insert(0, indentString);
      for ( int i = 0; i < string.length(); i++ ) {
         if ( string.charAt(i) == '\n' ) {
            string.insert(i + 1, indentString);
            i += indentString.length();
         }
      }
      return string;
   }

   /**
    * Liefert <code>true</code> für alle lesbaren, also anzeigbaren <code>bytes</code>.<p>
    * <code>false</code> gilt für alle Zeichen wie <code>DEL</code>, <code>Backspace</code>
    * oder <code>'\0'</code>.
    * @param b ein beliebiges <code>byte</code>
    * @return <code>true</code>, falls das Zeichen lesbar ist, <code>false</code> wenn nicht.
    */
   public static final boolean isPrintable( byte b ) {
      return ((b > 31) && (b < 127)) || (b > 161); // lesbare Zeichen
   }

   /** 
    * Computes the levenstein distance, aka edit distance, for two strings.
    * The levenstein distance is defined as the number of edit operations needed to get from String A to String B.
   */
   public static int levensteinDistance( String s, String t, int upperBound ) {
      final int m = t.length();
      final int n = s.length();

      int[] d = getInt(_d, n + 1);
      int[] p = getInt(_p, n + 1);
      // init matrix d
      for ( int i = 0; i <= n; ++i ) {
         p[i] = i;
      }

      char[] sChars = s.toCharArray();

      // start computing edit distance
      for ( int j = 1; j <= m; ++j ) { // iterates through target
         int bestPossibleEditDistance = m;
         final char t_j = t.charAt(j - 1); // jth character of t
         d[0] = j;

         for ( int i = 1; i <= n; ++i ) { // iterates through text
            // minimum of cell to the left+1, to the top+1, diagonally left and up +(0|1)
            int iMinusOne = i - 1;
            if ( t_j != sChars[iMinusOne] ) {
               d[i] = Math.min(Math.min(d[iMinusOne], p[i]), p[iMinusOne]) + 1;
            } else {
               d[i] = Math.min(Math.min(d[iMinusOne] + 1, p[i] + 1), p[iMinusOne]);
            }
            bestPossibleEditDistance = Math.min(bestPossibleEditDistance, d[i]);
         }

         //After calculating row i, the best possible edit distance
         //can be found by found by finding the smallest value in a given column.
         //If the bestPossibleEditDistance is greater than the max distance, abort.

         if ( j > upperBound && bestPossibleEditDistance > upperBound ) { //equal is okay, but not greater
            //the closest the target can be to the text is just too far away.
            //this target is leaving the party early.
            return Integer.MAX_VALUE;
         }

         // copy current distance counts to 'previous row' distance counts: swap p and d
         int _d[] = p;
         p = d;
         d = _d;
      }

      // our last action in the above loop was to switch d and p, so p now
      // actually has the most recent cost counts
      return p[n];
   }

   public static String notNull( String s ) {
      return notNull(s, "");
   }

   public static String notNull( String s, String r ) {
      return (s != null) ? s : r;
   }

   /**
    * Effiziente Implementierung für String-Ersetzungen.
    * @param str zu bearbeitender <code>String</code>
    * @param search Suchbegriff
    * @param rep Ersetzbegriff
    * @return <code>String</code> in dem alle Vorkommnisse von <code>search</code> durch
    *         <code>rep</code> ersetzt wurden.
    */
   public final static String replace( String str, String search, String rep ) {

      if ( (str == null) || (search == null) ) {
         return str;
      }

      return replace_intern(str, new char[][] { search.toCharArray() }, new char[][] { (rep == null) ? new char[] { Character.MIN_VALUE } : rep.toCharArray() });
   }

   /**
    * Effiziente Implementierung für String-Ersetzungen.
    * @param str zu bearbeitender <code>String</code>
    * @param search Suchbegriffe
    * @param rep Ersetzbegriffe
    * @return <code>String</code> in dem alle Vorkommnisse von allen Elementen in <code>search[]</code> durch
    *         ihre Entsprechungen in <code>rep[]</code> ersetzt wurden.
    */
   public final static String replace( String str, String[] search, String[] rep ) {

      if ( (str == null) || (search == null) || (rep == null) ) {
         return str;
      }
      if ( search.length < rep.length ) {
         throw new IllegalArgumentException("Not enough elements in 'rep'.");
      }

      char[][] s = new char[search.length][];
      char[][] r = new char[search.length][];
      for ( int i = 0; i < s.length; i++ ) {
         s[i] = (search[i] != null) ? search[i].toCharArray() : null;
         if ( (i < rep.length) && (rep[i] != null) ) {
            r[i] = rep[i].toCharArray();
         } else {
            r[i] = new char[] { Character.MIN_VALUE };
         }
      }
      return replace_intern(str, s, r);
   }

   /**
    * Behaves as String.split(.) or Pattern.compile(.).split(.) with a single char String as parameter.<p/>
    * <b>Exception</b>: Splitting of an empty String, where this method returns an empty array, while String.split() returns an array containing a single empty String.<p/> 
    * Only difference is performance, this implementation is significantly faster.  
    */
   public static String[] split( String s, char sep ) {
      List<String> tokens = new ArrayList<String>();
      StringBuilder token = new StringBuilder();
      int i = 0;
      for ( int length = s.length(); i < length; i++ ) {
         char c = s.charAt(i);
         if ( c == sep ) {
            tokens.add(token.toString());
            token.setLength(0);
         } else {
            token.append(c);
         }
      }
      if ( token.length() > 0 || i == 0 ) {
         tokens.add(token.toString());
      }

      // remove trailing empty Strings to simulate String.split(.) 
      for ( int j = tokens.size() - 1; j >= 0; j-- ) {
         if ( (tokens.get(j)).length() == 0 ) {
            tokens.remove(j);
         } else {
            break;
         }
      }

      return tokens.toArray(new String[tokens.size()]);
   }

   /**
    * Liefert eine HexEditor Ansicht von <code>s</code>.<p>
    * Beispiel:
    * <pre><code>
    * 4c 69 65 66 65 72 74 20 65 69 6e 65 20 48 65 78  Liefert eine Hex
    * 45 64 69 74 6f 72 20 41 6e 73 69 63 68 74 20 76  Editor Ansicht v
    * 6f 6e 20 73 2e                                   on s.
    * </code></pre>
    * @param s ein beliebiger <code>String</code>
    * @return
    */
   public static final String toHex( String s ) {

      StringBuffer hex = new StringBuffer();

      int i;
      for ( i = 0; i < s.length(); i++ ) {
         byte c = (byte)s.charAt(i);
         if ( c < 0 ) {
            String hexString = Integer.toHexString(c);
            hex.append(hexString.substring(hexString.length() - 2)).append(' ');
         } else {
            if ( c < 16 ) {
               hex.append('0');
            } else if ( c > 256 ) {
               throw new UnsupportedOperationException("Unicode characters not supported.");
            }
            hex.append(Integer.toHexString(c)).append(' ');
         }
         if ( (i != 0) && (i % 16 == 15) ) {
            hex.append(' ');
            for ( int j = i - 15; j <= i; j++ ) {
               if ( isPrintable((byte)s.charAt(j)) ) {
                  hex.append(s.charAt(j));
               } else {
                  hex.append('.');
               }
            }
            hex.append('\n');
         }
      }

      if ( (i != 0) && (i % 16 != 0) ) {
         int j = i - 1;
         while ( j % 16 != 15 ) {
            hex.append("   ");
            j++;
         }

         hex.append(' ');

         int max = s.length();
         for ( j = i - (i % 16); j < max; j++ ) {
            if ( isPrintable((byte)s.charAt(j)) ) {
               hex.append(s.charAt(j));
            } else {
               hex.append('.');
            }
         }
      }

      return hex.toString();
   }

   /**
    * Basierend auf <code>text</code> wird ein <code>String[]</code> erzeugt, wobei jedes Vorkommen von
    * <code>delim</code> ein neues Feld bewirkt.
    * @param text Ursprungstext
    * @param delim Trennzeichen bzw. Trenn<code>String</code>
    * @param returndelim Falls <code>true</code>, wird das Trennzeichen als eigener <code>String</code> im
    *                    <code>String[]</code> zurückgeliefert.
    * @return String[] mit allen Tokens
    */
   public static final String[] tokenize( String text, String delim, boolean returndelim ) {
      return tokenize(text, delim, returndelim, false);
   }

   /**
    * Basierend auf <code>text</code> wird ein <code>String[]</code> erzeugt, wobei jedes Vorkommen von
    * <code>delim</code> ein neues Feld bewirkt.
    * @param text Ursprungstext
    * @param delim Trennzeichen bzw. Trenn<code>String</code>
    * @param returndelim Falls <code>true</code>, wird das Trennzeichen als eigener <code>String</code> im
    *                    <code>String[]</code> zurückgeliefert.
    * @param ignoreEmptyStrings Leerstrings werden ignoriert falls true
    * @return String[] mit allen Tokens
    */
   public static final String[] tokenize( String text, String delim, boolean returndelim, boolean ignoreEmptyStrings ) {

      if ( text == null ) {
         return new String[0];
      }
      ArrayList splitlist = new ArrayList(100);
      StringTokenizer tok = new StringTokenizer(text, delim, returndelim);
      while ( tok.hasMoreTokens() ) {
         String token = tok.nextToken();
         if ( (!ignoreEmptyStrings) || (token.length() > 0) ) {
            splitlist.add(token);
         }
      }
      return (String[])splitlist.toArray(new String[splitlist.size()]);
   }

   public static String trimToNull( String s ) {
      if ( s == null ) {
         return s;
      }
      return (s.trim().length() == 0) ? null : s.trim();
   }

   public static String unescape( String str ) {
      if ( str == null ) {
         return null;
      }
      int len = str.length();
      StringBuilder sb = new StringBuilder(len);
      char c;

      for ( int i = 0; i < len; i++ ) {
         c = str.charAt(i);
         if ( c == ESCAPE_CHAR ) {
            c = str.charAt(++i);
            switch ( c ) {
            case ESCAPE_CHAR:
               sb.append(ESCAPE_CHAR);
               break;
            case ESCAPE_SEPARATOR:
               sb.append(SEPARATOR);
               break;
            case ESCAPE_NEW_LINE:
               sb.append(NEW_LINE);
               break;
            }
         } else {
            sb.append(c);
         }
      }
      return sb.toString();
   }

   public static String unique( String value ) {
      String ret = value;

      if ( !_cachingConfigured ) {
         synchronized ( _uniqueStrings ) {
            if ( !_cachingConfigured ) {
               _cachingConfigured = true;
               _cachingDisabled = Boolean.getBoolean("util.string.StringTool.unique.disabled");
            }
         }
      }

      if ( !_cachingDisabled ) {
         synchronized ( _uniqueStrings ) {
            WeakReference weakReference = (WeakReference)_uniqueStrings.get(value);
            if ( weakReference == null ) {
               _uniqueStrings.put(value, new WeakReference(value));
            } else {
               ret = (String)weakReference.get();
            }
         }
      }
      return ret;

   }

   private static int[] getInt( ThreadLocal<int[]> store, int n ) {
      int[] i = store.get();
      if ( i.length < n ) {
         i = new int[n];
         store.set(i);
      }
      return i;
   }

   /*
    public static void main(String[] args) {


    String text = "hallo&auml; -- &ouml; -- &uuml; \n &Auml; -- &Ouml; -- &Uuml; \n &szlig;Hallo";
    String[] _search = new String[] {"&uuml;","&auml;","&ouml;","&Uuml;","&Auml;","&Ouml;","&szlig;"};
    String[] _rep = new String[] {"ü","ä","ö","Ü","Ä","Ö"};
    //String text = "";



    //String _search = "";
    //String _rep = "umlaut;";
    long t1 = System.currentTimeMillis();
    for (int i=0;i<100000;i++) {
    String ret = replace(text,_search,_rep);
    }
    System.out.println(" Zeit: " + (System.currentTimeMillis()-t1));

    }
    */

   private static final int min( final int a, final int b, final int c ) {
      int t = (a < b) ? a : b;
      return (t < c) ? t : c;
   }

   private final static String replace_intern( String str, char[][] search, char[][] rep ) {

      if ( (str == null) || (search == null) || (rep == null) ) {
         return str;
      }
      char[] field = str.toCharArray();
      StringBuffer sb = new StringBuffer(field.length + 200);
      int i1 = 0, i2 = 0;
      while ( i2 < field.length ) {
         boolean matched = false;
         int k = 0;
         for ( k = 0; k < search.length; k++ ) {
            if ( (search[k] != null) && (search[k].length > 0) && (replace_intern_matches(field, i2, search[k])) ) {
               sb.append(field, i1, i2 - i1);
               sb.append(rep[k]);
               i1 = i2 = i2 + search[k].length;
               matched = true;
               break;
            }
         }
         if ( !matched ) {
            i2++;
         }
      }
      if ( i2 > i1 ) {
         sb.append(field, i1, i2 - i1);
      }
      return sb.toString();
   }

   private static final boolean replace_intern_matches( char[] field, int offset, char[] search ) {

      for ( int i = 0; i < search.length; i++ ) {
         if ( (offset + i >= field.length) || (field[offset + i] != search[i]) ) {
            return false;
         }
      }
      return true;
   }
}