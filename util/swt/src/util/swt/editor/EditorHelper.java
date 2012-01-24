package util.swt.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ExtendedModifyEvent;
import org.eclipse.swt.custom.ExtendedModifyListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

import util.swt.SWTUtils;
import util.swt.editor.Template.TemplatePosition;
import util.swt.event.OnEvent;


public class EditorHelper {

   private static Pattern ALLOWED_SEARCH_CHARS    = Pattern.compile("[\\p{Print}\\p{InLATIN_1_SUPPLEMENT}]");

   ApplicationWindow      _window;
   StyledText             _editor;
   String                 _extension;
   Stack<TextChange>      _changes                = new Stack<TextChange>();
   boolean                _ignoreUndo             = false;
   long                   _lastChangeTime         = 0;
   boolean                _redrawOnChange         = true;
   RegexHighlighter       _lineStyleListener;
   Map<String, Template>  _templates;
   Map<String, Keyword>   _keywords;
   String                 _incrementalSearchString;
   String                 _lastIncrementalSearchString;

   int                    _lastExpandWordPosition = -1;
   Set<String>            _expandWordVetos        = new HashSet<String>();
   String                 _lastExpandWord;

   AutoCompleteShell      _autoCompleteShell;


   /**
    * @param window may be null - if not null, status line updates will be executed on some actions
    */
   public EditorHelper( ApplicationWindow window, StyledText editor, String extension ) {
      _window = window;
      _editor = editor;
      _extension = extension;

      OnEvent.addListener(_editor, 3005 /* StyledText.VerifyKey */).on(EditorHelper.class, this).verifyKey(null);
      OnEvent.addListener(_editor, SWT.KeyDown).on(EditorHelper.class, this).keyDown(null);
      OnEvent.addListener(_editor, SWT.FocusOut).on(EditorHelper.class, this).focusOut(null);
      OnEvent.addListener(_editor, SWT.MouseDown).on(EditorHelper.class, this).mouseDown(null);
      OnEvent.addListener(_editor, 3011 /*StyledText.CaretMoved*/).on(EditorHelper.class, this).caretMoved(null);

      // Store undo information
      _editor.addExtendedModifyListener(new ExtendedModifyListener() {

         public void modifyText( ExtendedModifyEvent event ) {
            if ( !_ignoreUndo ) {
               boolean lastChangeWasRecently = System.currentTimeMillis() - _lastChangeTime < 1000;
               TextChange lastChange = _changes.size() > 0 ? _changes.lastElement() : null;
               if ( lastChangeWasRecently && lastChange != null && event.length == 1 && event.replacedText.isEmpty()
                  && lastChange.start + lastChange.length == event.start ) {
                  lastChange.length++;
               } else if ( lastChangeWasRecently && lastChange != null && event.length == 0 && event.replacedText.length() == 1
                  && lastChange.start - event.replacedText.length() == event.start ) {
                  lastChange.replacedText = event.replacedText + lastChange.replacedText;
                  lastChange.start = event.start;
               } else {
                  // Push this change onto the changes stack
                  _changes.push(new TextChange(event.start, event.length, event.replacedText, _editor.getTopIndex()));
               }
               _lastChangeTime = System.currentTimeMillis();
            }
         }
      });

      _editor.addModifyListener(new ModifyListener() {

         public void modifyText( ModifyEvent event ) {
            // Update the comments
            if ( _lineStyleListener != null ) {
               _lineStyleListener.refreshMultilineComments(_editor.getText());
               if ( _redrawOnChange ) {
                  _editor.redraw();
               }
            }
         }
      });

      _templates = TemplateManager.getTemplates(extension);
      _keywords = KeywordManager.getKeywords(extension);
      _lineStyleListener = new RegexHighlighter(extension);
      _editor.addLineStyleListener(_lineStyleListener);
   }

   public EditorHelper( StyledText editor, String extension ) {
      this(null, editor, extension);
   }

   public void clearHistory() {
      _changes.clear();
   }

   public void setErrorLines( int[] lines ) {
      _lineStyleListener.setErrorLines(lines);
   }

   public void setIgnoreUndo( boolean ignoreUndo ) {
      _ignoreUndo = ignoreUndo;
   }

   public void setRedrawOnChange( boolean redrawOnChange ) {
      _redrawOnChange = redrawOnChange;
   }

   void afterFocusOut( Event e ) {
      if ( _editor.isDisposed() || _autoCompleteShell == null || _autoCompleteShell._shell.isDisposed() ) {
         return;
      }
      Control focusControl = _editor.getDisplay().getFocusControl();
      if ( _autoCompleteShell != null && focusControl != _autoCompleteShell._shell
         && (focusControl == null || focusControl.getShell() != _autoCompleteShell._shell) ) {
         _autoCompleteShell._shell.dispose();
      }
   }

   void caretMoved( Event e ) {
      int caretOffset = _editor.getCaretOffset();
      String text = _editor.getText();

      int oldHighlightIndex = _lineStyleListener._highlightedCharIndex;

      _lineStyleListener.highlightBraceMatch(text, caretOffset);

      if ( oldHighlightIndex != _lineStyleListener._highlightedCharIndex ) {
         if ( oldHighlightIndex >= 0 ) {
            _editor.redrawRange(oldHighlightIndex, 1, false);
         }
         if ( _lineStyleListener._highlightedCharIndex >= 0 ) {
            _editor.redrawRange(_lineStyleListener._highlightedCharIndex, 1, false);
         }
      }
   }

   boolean expandKeyword( String text, int caretPos, String keywordHandle, String word ) {
      Keyword keyword = _keywords.get(keywordHandle);
      if ( keyword == null ) {
         List<Keyword> keywords = autoCompleteKeyword(keywordHandle);
         if ( keywords.size() == 1 ) {
            keyword = keywords.get(0);
         }
      }
      if ( keyword != null ) {
         _editor.replaceTextRange(caretPos - word.length(), word.length(), keyword._handle);
         _editor.setCaretOffset(caretPos + keyword._handle.length() - word.length());
      }
      return keyword != null;
   }

   boolean expandTemplate( String text, int caretPos, String templateHandle, String word ) {
      Template template = _templates.get(templateHandle);
      if ( template == null ) {
         List<Template> templates = autoCompleteTemplate(templateHandle);
         if ( templates.size() == 1 ) {
            template = templates.get(0);
         }
      }
      if ( template != null ) {
         int caretOffsetAtLine = caretPos - _editor.getOffsetAtLine(_editor.getLineAtOffset(caretPos));
         TemplatePosition templatePosition = template.getTemplate(caretOffsetAtLine);
         _editor.replaceTextRange(caretPos - word.length(), word.length(), templatePosition._textToInsert);
         _editor.setCaretOffset(caretPos + templatePosition._caretOffset - word.length());
      }
      return template != null;
   }

   boolean expandWord( String text, int caretPos, String wordToSearch ) {
      String wordToReplace = wordToSearch;
      if ( caretPos != _lastExpandWordPosition ) {
         _expandWordVetos.clear();
         _lastExpandWord = wordToSearch;
      } else {
         wordToSearch = _lastExpandWord;
      }

      Set<String> tokenSet = getTokens(text);
      tokenSet.remove(wordToSearch);
      tokenSet.removeAll(_expandWordVetos);
      for ( String t : tokenSet ) {
         if ( t.startsWith(wordToSearch) ) {
            _editor.replaceTextRange(caretPos - wordToReplace.length(), wordToReplace.length(), t);
            _lastExpandWordPosition = caretPos + t.length() - wordToReplace.length();
            _editor.setCaretOffset(_lastExpandWordPosition);
            _expandWordVetos.add(t);
            return true;
         }
      }
      return false;
   }

   void focusOut( Event e ) {
      SWTUtils.asyncExec(_editor.getDisplay()).on(getClass(), this).afterFocusOut(null);
   }

   void keyDown( Event e ) {
      // TODO extract xml specific actions into subclass XMLEditorHelper
      if ( e.stateMask == SWT.CONTROL && (e.keyCode == 'f' || e.keyCode == 'j') ) {
         _incrementalSearchString = "";
         updateIncrementalSearchStatus();
      }
      if ( e.stateMask == SWT.CONTROL && e.keyCode == 'k' ) {
         _incrementalSearchString = _editor.getSelectionText();
         if ( _incrementalSearchString.isEmpty() ) {
            _incrementalSearchString = _lastIncrementalSearchString;
         }
         updateIncrementalSearchStatus();
         searchForward(true);
      }
      if ( e.stateMask == (SWT.CONTROL | SWT.SHIFT) && e.keyCode == 'k' ) {
         _incrementalSearchString = _editor.getSelectionText();
         updateIncrementalSearchStatus();
         searchBackwards();
      }
      if ( e.stateMask == SWT.CONTROL && e.keyCode == 'z' ) {
         undo();
      }
      if ( e.stateMask == SWT.CONTROL && e.keyCode == ' ' ) {
         autoComplete(true);
      }
      if ( _autoCompleteShell != null && (e.stateMask == 0 || e.stateMask == SWT.SHIFT) && !isAutoCompleteKey(e) ) {
         autoComplete(false);
      }
      if ( e.stateMask == SWT.CONTROL && e.keyCode == 'a' ) {
         _editor.selectAll();
      }
      if ( e.stateMask == SWT.CONTROL && e.keyCode == 'd' ) {
         deleteLines();
      }
      if ( e.stateMask == SWT.CONTROL && e.keyCode == 'i' ) {
         undo(); // eat the last edit
         indent();
      }
      if ( e.stateMask == (SWT.CONTROL) && e.keyCode == SWT.ARROW_UP ) {
         _editor.setTopIndex(_editor.getTopIndex() - 1);
      }
      if ( e.stateMask == (SWT.CONTROL) && e.keyCode == SWT.ARROW_DOWN ) {
         _editor.setTopIndex(_editor.getTopIndex() + 1);
      }
      if ( e.stateMask == (SWT.ALT) && e.keyCode == SWT.ARROW_UP ) {
         moveLinesUp();
      }
      if ( e.stateMask == (SWT.ALT) && e.keyCode == SWT.ARROW_DOWN ) {
         moveLinesDown();
      }
      if ( e.stateMask == (SWT.ALT | SWT.CTRL) && e.keyCode == SWT.ARROW_UP ) {
         copyLinesUp();
      }
      if ( e.stateMask == (SWT.ALT | SWT.CTRL) && e.keyCode == SWT.ARROW_DOWN ) {
         copyLinesDown();
      }
      if ( e.character == '>' ) {
         closeXmlTag();
      }
   }

   void mouseDown( Event e ) {
      if ( _autoCompleteShell != null && !_autoCompleteShell._shell.getBounds().contains(_editor.toDisplay(e.x, e.y)) ) {
         _autoCompleteShell._shell.dispose();
      }
   }

   void undo() {
      // Make sure undo stack isn't empty
      if ( !_changes.empty() ) {
         // Get the last change
         TextChange change = _changes.pop();

         // Set the flag. Otherwise, the replaceTextRange call will get placed
         // on the undo stack
         _ignoreUndo = true;
         // Replace the changed text
         _editor.replaceTextRange(change.getStart(), change.getLength(), change.getReplacedText());

         // Move the caret
         _editor.setCaretOffset(change.getStart());

         // Scroll the screen
         _editor.setTopIndex(change.getTopIndex());
         _ignoreUndo = false;
      }
   }

   void verifyKey( Event e ) {
      if ( isAutoCompleteKey(e) && _autoCompleteShell != null ) {
         _autoCompleteShell.keyDown(e);
         e.doit = false;
         return;
      }
      if ( _incrementalSearchString != null ) {
         keyDownIncrementalSearch(e);
         e.doit = false;
      }
      if ( e.stateMask == 0 && e.keyCode == SWT.CR ) {
         indentNewLine();
         e.doit = false;
      }
      if ( e.stateMask == 0 && e.keyCode == SWT.HOME ) {
         smartHome();
         e.doit = false;
      }
   }

   private void autoComplete( boolean autoExpand ) {
      if ( _autoCompleteShell != null ) {
         _autoCompleteShell._shell.dispose();
      }

      if ( (_editor.getStyle() & SWT.READ_ONLY) != 0 ) {
         return;
      }

      String text = _editor.getText();
      int pos = _editor.getCaretOffset();
      StringBuilder word = new StringBuilder();
      for ( int i = pos - 1; i >= 0; i-- ) {
         char c = text.charAt(i);
         if ( !Character.isJavaIdentifierPart(c) || c == '$' ) {
            break;
         }
         word.insert(0, c);
      }
      if ( word.length() == 0 ) {
         // for some weird one char templates like @
         if ( _templates.containsKey("" + text.charAt(pos - 1)) ) {
            word.append(text.charAt(pos - 1));
         }
      }

      String w = word.toString().trim();
      List<Template> templates = autoCompleteTemplate(w);
      List<Keyword> keywords = autoCompleteKeyword(w);
      List<String> words = autoCompleteWord(text, w);
      if ( words.size() + templates.size() + keywords.size() > 1 ) {
         _autoCompleteShell = new AutoCompleteShell(this, pos, w, templates, words, keywords);
         _autoCompleteShell.open();
      } else if ( autoExpand && words.size() == 1 ) {
         expandWord(text, pos, w);
      } else if ( autoExpand && keywords.size() == 1 ) {
         expandKeyword(text, pos, w, w);
      } else if ( autoExpand && templates.size() == 1 ) {
         expandTemplate(text, pos, w, w);
      }
   }

   private List<Keyword> autoCompleteKeyword( String wordToSearch ) {
      List<Keyword> propositions = new ArrayList<Keyword>();
      for ( Keyword keyword : _keywords.values() ) {
         if ( keyword._handle.startsWith(wordToSearch) ) {
            propositions.add(keyword);
         }
      }
      return propositions;
   }

   private List<Template> autoCompleteTemplate( String wordToSearch ) {
      List<Template> propositions = new ArrayList<Template>();
      for ( Template template : _templates.values() ) {
         if ( template._handle.startsWith(wordToSearch) ) {
            propositions.add(template);
         }
      }
      return propositions;
   }

   private List<String> autoCompleteWord( String text, String wordToSearch ) {
      List<String> propositions = new ArrayList<String>();
      Set<String> tokenSet = getTokens(text);
      tokenSet.remove(wordToSearch);
      for ( String t : tokenSet ) {
         if ( t.startsWith(wordToSearch) ) {
            propositions.add(t);
         }
      }
      return propositions;
   }

   private void closeXmlTag() {
      int caretOffset = _editor.getCaretOffset();
      int lineIndex = _editor.getLineAtOffset(caretOffset);
      int lineOffset = _editor.getOffsetAtLine(lineIndex);
      int indexInLine = caretOffset - lineOffset - 1;
      String line = _editor.getLine(lineIndex);
      if ( indexInLine == 0 ) {
         return;
      }
      if ( line.charAt(indexInLine - 1) == '/' ) {
         return;
      }
      int openingTagIndex = line.lastIndexOf('<', indexInLine);
      if ( openingTagIndex < 0 ) {
         return;
      }
      if ( line.charAt(openingTagIndex + 1) == '/' ) {
         return;
      }
      int tagnameEndIndex = line.indexOf(' ', openingTagIndex);
      if ( tagnameEndIndex < 0 ) {
         tagnameEndIndex = indexInLine;
      }
      String tagname = line.substring(openingTagIndex + 1, tagnameEndIndex);
      _editor.insert("</" + tagname + ">");
      //      StringBuilder indent = new StringBuilder();
      //      for ( int i = 0; i < openingTagIndex; i++ ) {
      //         indent.append(' ');
      //      }
      //      _editor.insert("\n" + indent + "  \n" + indent + "</" + tagname + ">");
      //      _editor.setCaretOffset(caretOffset + 1 + indent.length() + 2);
   }

   private void copyLinesDown() {
      expandSelection();

      _editor.replaceTextRange(_editor.getSelection().x, 0, _editor.getSelectionText() + "\r\n");
   }

   private void copyLinesUp() {
      expandSelection();

      _editor.replaceTextRange(_editor.getSelection().y, 0, "\r\n" + _editor.getSelectionText());
   }

   private void deleteLines() {
      expandSelection();
      Point selection = _editor.getSelection();
      int start = selection.x;
      int nextLine = _editor.getLineAtOffset(selection.y) + 1;
      int nextLineOffset = _editor.getOffsetAtLine(nextLine);
      _editor.replaceTextRange(start, nextLineOffset - start, "");
   }

   private void expandSelection() {
      Point selection = _editor.getSelection();
      int firstLine = _editor.getLineAtOffset(selection.x);
      int lastLine = _editor.getLineAtOffset(selection.y);
      Point newSelection = new Point(0, 0);
      newSelection.x = _editor.getOffsetAtLine(firstLine);
      boolean addLine = _editor.getOffsetAtLine(lastLine) != selection.y || selection.y - selection.x == 0;
      lastLine -= addLine ? 0 : 1;
      newSelection.y = _editor.getOffsetAtLine(lastLine) + _editor.getLine(lastLine).length();
      _editor.setSelection(newSelection);
   }

   private Set<String> getTokens( String text ) {
      String[] tokens = text.split("([^\\p{javaJavaIdentifierPart}]|\\$)");
      Set<String> tokenSet = new TreeSet<String>(Arrays.asList(tokens));
      return tokenSet;
   }

   private void indent() {
      int topIndex = _editor.getTopIndex();
      String text = _editor.getText();
      int line = _editor.getLineAtOffset(_editor.getCaretOffset());
      int col = _editor.getCaretOffset() - _editor.getOffsetAtLine(line);
      text = XMLIndenter.indent(text);
      _editor.setText(text);
      _editor.setCaretOffset(_editor.getOffsetAtLine(line) + col);
      _editor.setTopIndex(topIndex);
   }

   private void indentNewLine() {
      String text = _editor.getText();
      int pos = _editor.getCaretOffset();
      StringBuilder sb = new StringBuilder(text);
      sb.insert(pos, "\n<indentNewLineMarker/>");
      String indentedText = XMLIndenter.indent(sb.toString());
      int markerIndex = indentedText.indexOf("<indentNewLineMarker/>");
      int indentLength = 0;
      for ( int i = markerIndex; i >= 0; i-- ) {
         char c = indentedText.charAt(i);
         if ( c == '\n' ) {
            indentLength = markerIndex - i - 1;
            break;
         }
      }

      StringBuilder indent = new StringBuilder("\n");
      for ( int i = 0; i < indentLength; i++ ) {
         indent.append(' ');
      }
      _editor.insert(indent.toString());
      _editor.setCaretOffset(pos + indent.length());
   }

   private boolean isAutoCompleteKey( Event e ) {
      return e.stateMask == 0 && //
         (e.keyCode == SWT.ARROW_UP || e.keyCode == SWT.ARROW_DOWN || e.keyCode == SWT.PAGE_UP || e.keyCode == SWT.PAGE_DOWN || e.keyCode == SWT.ESC || e.keyCode == SWT.CR);
   }

   private void keyDownIncrementalSearch( Event e ) {
      if ( e.keyCode == SWT.SHIFT ) {
         return;
      }
      char c = e.character;
      if ( e.keyCode == SWT.ARROW_UP ) {
         searchBackwards();
      } else if ( e.keyCode == SWT.ARROW_DOWN ) {
         searchForward(true);
      } else if ( e.keyCode == SWT.BS ) {
         _incrementalSearchString = _incrementalSearchString.substring(0, Math.max(0, _incrementalSearchString.length() - 1));
         searchForward(false);
         updateIncrementalSearchStatus();
      } else if ( ALLOWED_SEARCH_CHARS.matcher("" + c).matches() ) {
         _incrementalSearchString += c;
         searchForward(false);
         updateIncrementalSearchStatus();
      } else {
         _incrementalSearchString = null;
         updateIncrementalSearchStatus();
      }
   }

   private void moveLinesDown() {
      expandSelection();

      Point selection = _editor.getSelection();
      int start = selection.x;
      int nextLine = _editor.getLineAtOffset(selection.y) + 1;
      int nextLineOffset = _editor.getOffsetAtLine(nextLine);
      int nextLineLength = _editor.getLine(nextLine).length();
      String lineAfterText = _editor.getLine(nextLine);
      String selectionText = _editor.getSelectionText();
      String s = lineAfterText + "\r\n" + selectionText;
      _editor.replaceTextRange(start, nextLineOffset + nextLineLength - start, s);
      _editor.setSelection(start + lineAfterText.length() + 2, start + s.length());
   }

   private void moveLinesUp() {
      expandSelection();

      Point selection = _editor.getSelection();
      int lineBefore = _editor.getLineAtOffset(selection.x) - 1;
      String lineBeforeText = _editor.getLine(lineBefore);
      String selectionText = _editor.getSelectionText();
      String s = selectionText + "\r\n" + lineBeforeText;
      int start = _editor.getOffsetAtLine(lineBefore);
      int nextLine = _editor.getLineAtOffset(selection.y);
      int nextLineOffset = _editor.getOffsetAtLine(nextLine);
      int nextLineLength = _editor.getLine(nextLine).length();
      _editor.replaceTextRange(start, nextLineOffset + nextLineLength - start, s);
      _editor.setSelection(start, start + selectionText.length());
   }

   private void searchBackwards() {
      Point selection = _editor.getSelection();
      String text = _editor.getText();
      int index = text.toLowerCase().lastIndexOf(_incrementalSearchString.toLowerCase(), selection.x - 1);
      if ( index >= 0 ) {
         _editor.setSelection(index, index + _incrementalSearchString.length());
      }
   }

   private void searchForward( boolean next ) {
      Point selection = _editor.getSelection();
      String text = _editor.getText();
      int index = text.toLowerCase().indexOf(_incrementalSearchString.toLowerCase(), next ? selection.y + 1 : selection.x);
      if ( index >= 0 ) {
         _editor.setSelection(index, index + _incrementalSearchString.length());
      }
   }

   private void smartHome() {
      int caretOffset = _editor.getCaretOffset();
      int lineNumber = _editor.getLineAtOffset(caretOffset);
      String line = _editor.getLine(lineNumber);
      int i = 0;
      for ( int length = line.length(); i < length; i++ ) {
         if ( !Character.isWhitespace(line.charAt(i)) ) {
            break;
         }
      }
      int offsetAtLine = _editor.getOffsetAtLine(lineNumber);
      int smartHomeOffset = offsetAtLine + i;
      if ( caretOffset == smartHomeOffset ) {
         i = 0;
      }
      _editor.setCaretOffset(offsetAtLine + i);
      _editor.showSelection();
   }

   private void updateIncrementalSearchStatus() {
      if ( _window != null ) {
         if ( _incrementalSearchString != null ) {
            _window.setStatus("incremental search " + _incrementalSearchString);
            if ( !_incrementalSearchString.trim().isEmpty() ) {
               _lastIncrementalSearchString = _incrementalSearchString;
            }
         } else {
            _window.setStatus(null);
         }
      }

      _lineStyleListener.setSearchString(_incrementalSearchString);
      _editor.redrawRange(0, _editor.getCharCount(), true); // re-apply all style ranges (also for the currently invisible lines!)
   }

}
