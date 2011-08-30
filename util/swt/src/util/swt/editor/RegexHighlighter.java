package util.swt.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.TextPresentation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.LineStyleListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import util.swt.editor.SyntaxData.StyleGroup;


public class RegexHighlighter implements LineStyleListener {

   // Colors
   private static final Color                    COMMENT_COLOR           = Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY);
   private static final Color                    COMMENT_BACKGROUND      = Display.getCurrent().getSystemColor(SWT.COLOR_WHITE);

   private static final StyleGroup               _searchStringStyleGroup = new StyleGroup("", null, "ffffa0", "0");

   // Holds the syntax data
   private SyntaxData                            syntaxData;

   // Holds the offsets for all multiline comments
   List                                          commentOffsets;

   private int[]                                 _errorLines             = new int[0];
   private Pattern                               _searchStringPattern;
   private Map<Pattern, NextOccurenceCacheEntry> _nextOccurenceCache     = new HashMap<Pattern, NextOccurenceCacheEntry>();


   public RegexHighlighter( String extension ) {
      this(SyntaxManager.getSyntaxData(extension));
   }

   RegexHighlighter( SyntaxData syntaxData ) {
      this.syntaxData = syntaxData;
      commentOffsets = new LinkedList();
   }

   /**
    * Called by StyledText to get styles for a line
    */
   public void lineGetStyle( LineStyleEvent event ) {
      // Only do styles if syntax data has been loaded
      if ( syntaxData != null ) {
         TextPresentation textPresentation = new TextPresentation();

         int start = 0;
         int lineNum = ((StyledText)event.widget).getLineAtOffset(event.lineOffset);
         int length = event.lineText.length();
         for ( int line : _errorLines ) {
            if ( lineNum == line ) {
               StyleRange s = new StyleRange(event.lineOffset, length, null, null, SWT.NORMAL);
               s.underline = true;
               s.underlineStyle = SWT.UNDERLINE_SQUIGGLE;
               s.underlineColor = Display.getDefault().getSystemColor(SWT.COLOR_RED);
               textPresentation.mergeStyleRange(s);
            }
         }

         String line = event.lineText;

         // Check if line begins inside a multiline comment
         int mlIndex = getBeginsInsideComment(event.lineOffset, event.lineText.length());
         if ( mlIndex > -1 ) {
            // Line begins inside multiline comment; create the range
            textPresentation.mergeStyleRange(new StyleRange(event.lineOffset, mlIndex - event.lineOffset, COMMENT_COLOR, COMMENT_BACKGROUND));
            start = mlIndex;
         }

         while ( start < length ) {
            Matcher minMatcher = null;
            StyleGroup minStyleGroup = null;
            if ( _searchStringPattern != null ) {
               Matcher m = findNextOccurence(line, _searchStringPattern, start);
               if ( m != null && (minMatcher == null || m.start() < minMatcher.start()) ) {
                  minMatcher = m;
                  minStyleGroup = _searchStringStyleGroup;
               }
            }
            for ( StyleGroup styleGroup : syntaxData.getStyleGroups() ) {
               Matcher m = findNextOccurence(line, styleGroup._regEx, start);
               if ( m != null && (minMatcher == null || m.start() < minMatcher.start()) ) {
                  minMatcher = m;
                  minStyleGroup = styleGroup;
               }
            }

            Matcher nextComment = findNextOccurence(line, syntaxData.getMultiLineCommentStart(), start);
            if ( nextComment != null && (minMatcher == null || nextComment.start() < minMatcher.start()) ) {
               // we have a comment - determine where comment ends
               Matcher endComment = findNextOccurence(line, syntaxData.getMultiLineCommentEnd(), start);

               // If comment doesn't end on this line, extend range to end of line
               int endCommentIndex;
               if ( endComment == null ) {
                  endCommentIndex = length;
               } else {
                  endCommentIndex = endComment.end();
               }
               textPresentation.mergeStyleRange(new StyleRange(event.lineOffset + start, endCommentIndex - start, COMMENT_COLOR, COMMENT_BACKGROUND));

               start = endCommentIndex;
            } else if ( minMatcher != null ) {
               int startIndex = minMatcher.start();
               int endIndex = minMatcher.end();
               StyleRange styleRange = new StyleRange(event.lineOffset + startIndex, endIndex - startIndex, minStyleGroup._foreground,
                  minStyleGroup._background, minStyleGroup._style);
               if ( minStyleGroup._underline ) {
                  styleRange.underline = true;
                  styleRange.underlineColor = minStyleGroup._underlineColor;
                  styleRange.underlineStyle = minStyleGroup._underlineStyle;
               }
               textPresentation.mergeStyleRange(styleRange);
               start = startIndex + 1;
            } else {
               break;
            }
         }

         List<StyleRange> styles = new ArrayList<StyleRange>();
         for ( Iterator<StyleRange> iterator = textPresentation.getAllStyleRangeIterator(); iterator.hasNext(); ) {
            styles.add(iterator.next());
         }

         // Copy the StyleRanges back into the event
         event.styles = styles.toArray(new StyleRange[styles.size()]);

         _nextOccurenceCache.clear();
      }
   }

   /**
    * Refreshes the offsets for all multiline comments in the parent StyledText.
    * The parent StyledText should call this whenever its text is modified. Note
    * that this code doesn't ignore comment markers inside strings.
    */
   public void refreshMultilineComments( String text ) {
      // Clear any stored offsets
      commentOffsets.clear();

      if ( syntaxData != null ) {
         int start = 0;
         while ( true ) {
            Matcher nextCommentStart = findNextOccurence(text, syntaxData.getMultiLineCommentStart(), start);
            if ( nextCommentStart == null ) {
               break;
            }
            Matcher nextCommentEnd = findNextOccurence(text, syntaxData.getMultiLineCommentEnd(), nextCommentStart.start());
            int endIndex = nextCommentEnd == null ? text.length() : nextCommentEnd.end();
            commentOffsets.add(new int[] { nextCommentStart.start(), endIndex });
            start = endIndex;
         }
      }
   }

   public void setErrorLines( int[] lines ) {
      _errorLines = lines;
   }

   public void setSearchString( String s ) {
      _searchStringPattern = s != null && s.length() > 1 ? Pattern.compile("(?i)" + Pattern.quote(s)) : null;
   }

   private Matcher findNextOccurence( String line, Pattern p, int startIndex ) {
      NextOccurenceCacheEntry cacheEntry = _nextOccurenceCache.get(p);
      if ( cacheEntry != null && line.equals(cacheEntry._line) && (cacheEntry._matcher == null || cacheEntry._matcher.start() >= startIndex) ) {
         return cacheEntry._matcher;
      }
      Matcher matcher = p.matcher(line);
      if ( matcher.find(startIndex) ) {
         _nextOccurenceCache.put(p, new NextOccurenceCacheEntry(line, matcher));
         return matcher;
      }
      _nextOccurenceCache.put(p, new NextOccurenceCacheEntry(line, null));
      return null;
   }

   /**
    * Checks to see if the specified section of text begins inside a multiline
    * comment. Returns the index of the closing comment, or the end of the line
    * if the whole line is inside the comment. Returns -1 if the line doesn't
    * begin inside a comment.
    * 
    * @param start
    *          the starting offset of the text
    * @param length
    *          the length of the text
    */
   private int getBeginsInsideComment( int start, int length ) {
      // Assume section doesn't being inside a comment
      int index = -1;

      // Go through the multiline comment ranges
      for ( int i = 0, n = commentOffsets.size(); i < n; i++ ) {
         int[] offsets = (int[])commentOffsets.get(i);

         // If starting offset is past range, quit
         if ( offsets[0] > start + length ) {
            break;
         }
         // Check to see if section begins inside a comment
         if ( offsets[0] <= start && offsets[1] >= start ) {
            // It does; determine if the closing comment marker is inside
            // this section
            index = offsets[1] > start + length ? start + length : offsets[1];
         }
      }
      return index;
   }


   private static class NextOccurenceCacheEntry {

      String  _line;
      Matcher _matcher;


      public NextOccurenceCacheEntry( String line, Matcher matcher ) {
         _line = line;
         _matcher = matcher;
      }
   }
}
