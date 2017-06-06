package util.swt.editor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class XMLIndenter {

   private static final Pattern TAG_START             = Pattern.compile("<(?![/!\\?])");
   private static final Pattern TAG_END               = Pattern.compile("(/>)|(</)");
   private static final Pattern ATTRIBUTE             = Pattern.compile("[\\w-:]*=");
   private static final Pattern LINE_START_WHITESPACE = Pattern.compile("^[\\s]*");


   public static String indent( String doc ) {
      String[] lines = doc.split("(\\r\\n)|\\n");
      int depth = 0, oldDepth = 0;
      boolean comment = false;
      int attributeIndex = -1, quoteIndex = -1;
      int quoteCount = 0;
      for ( int i = 0, length = lines.length; i < length; i++ ) {
         oldDepth = depth;

         int commentBeginIndex = lines[i].indexOf("<!--");
         int commentEndIndex = lines[i].indexOf("-->");

         int openTagIndex = -1, closeTagIndex = -1;
         Matcher matcher = TAG_START.matcher(lines[i]);
         while ( matcher.find() && !comment(matcher.start(), commentBeginIndex, commentEndIndex, comment) ) {
            depth++;
            openTagIndex = matcher.start();
         }

         int index = -1;
         while ( (index = lines[i].indexOf(">", index + 1)) >= 0 && !comment(index, commentBeginIndex, commentEndIndex, comment)
            && lines[i].charAt(index - 1) != '-' ) {
            closeTagIndex = index;
         }

         matcher = TAG_END.matcher(lines[i]);
         while ( matcher.find() && !comment(matcher.start(), commentBeginIndex, commentEndIndex, comment) ) {
            depth--;
         }

         String indentedLine = indentLine(lines[i], depth > oldDepth ? oldDepth : depth, attributeIndex, quoteCount % 2 == 1 ? quoteIndex : -1);

         // Kommentar-Position in der eingerückten Zeile neu berechnen
         commentBeginIndex = indentedLine.indexOf("<!--");
         commentEndIndex = indentedLine.indexOf("-->");

         index = -1;
         while ( (index = indentedLine.indexOf("\"", index + 1)) >= 0 && !comment(index, commentBeginIndex, commentEndIndex, comment)
            && !apostrophe(indentedLine, index) ) {
            quoteCount++;
            quoteIndex = index + 1;
         }

         if ( openTagIndex > closeTagIndex ) {
            matcher = ATTRIBUTE.matcher(indentedLine);
            if ( matcher.find() ) {
               attributeIndex = matcher.start();
            } else {
               attributeIndex = depth;
            }
         }
         if ( openTagIndex < closeTagIndex ) {
            attributeIndex = -1;
         }

         if ( commentBeginIndex > commentEndIndex ) {
            comment = true;
         }
         if ( commentBeginIndex < commentEndIndex ) {
            comment = false;
         }

         lines[i] = indentedLine;
      }

      StringBuffer newDoc = new StringBuffer();
      for ( int i = 0; i < lines.length; i++ ) {
         newDoc.append(lines[i]).append("\n");
      }
      return newDoc.toString();
   }

   private static boolean apostrophe( String indentedLine, int index ) {
      int lastAposStart = indentedLine.lastIndexOf("='", index);
      int nextAposEnd = indentedLine.indexOf("'", index);
      return lastAposStart >= 0 && nextAposEnd >= 0 && lastAposStart < index && index < nextAposEnd;
   }

   private static boolean comment( int index, int commentBeginIndex, int commentEndIndex, boolean comment ) {
      if ( comment && (commentEndIndex == -1 || commentEndIndex > index) ) {
         return true;
      }
      if ( commentBeginIndex != -1 && commentBeginIndex < index && (commentEndIndex == -1 || commentEndIndex > index) ) {
         return true;
      }
      return false;
   }

   private static String indentLine( String line, int depth, int attributeIndex, int quoteIndex ) {
      Matcher matcher = LINE_START_WHITESPACE.matcher(line);
      int firstRealCharIndex = 0;
      if ( matcher.find() ) {
         firstRealCharIndex = matcher.end();
      }
      StringBuffer indent = new StringBuffer();
      int indentSize = depth * 2;
      if ( attributeIndex >= 0 ) {
         indentSize = attributeIndex;
      }
      if ( quoteIndex >= 0 ) {
         indentSize = quoteIndex;
      }
      for ( int i = 0; i < indentSize; i++ ) {
         indent.append(' ');
      }

      return indent + line.substring(firstRealCharIndex);
   }
}
