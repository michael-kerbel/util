package util.swt.editor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.custom.StyledText;

import util.string.StringTool;


public class Template {

   private static final Pattern CARET = Pattern.compile("\\|");

   public String                _handle;
   public String                _name;
   public String                _template;


   public Template( String handle, String name, String template ) {
      _handle = handle;
      _name = name;
      _template = template;
   }

   public void expand( StyledText editor, int caretPos, String templateHandle ) {
      int caretOffsetAtLine = caretPos - editor.getOffsetAtLine(editor.getLineAtOffset(caretPos));
      TemplatePosition templatePosition = getTemplate(caretOffsetAtLine);
      editor.replaceTextRange(caretPos - templateHandle.length(), templateHandle.length(), templatePosition._textToInsert);
      editor.setCaretOffset(caretPos + templatePosition._caretOffset - templateHandle.length());
   }

   public TemplatePosition getTemplate( int currentCol ) {
      StringBuilder sb = new StringBuilder();
      for ( int i = 0; i < currentCol; i++ ) {
         sb.append(' ');
      }
      String indentedTemplate = StringTool.indent(_template, sb.toString()).toString();
      indentedTemplate = indentedTemplate.substring(sb.length());
      Matcher m = CARET.matcher(indentedTemplate);
      int caretOffset = 0;
      if ( m.find() ) {
         caretOffset = m.start();
         indentedTemplate = m.replaceFirst("");
      }
      return new TemplatePosition(indentedTemplate, caretOffset);
   }

   @Override
   public String toString() {
      return _handle;
   }


   public static class TemplatePosition {

      String _textToInsert;
      int    _caretOffset;


      public TemplatePosition( String textToInsert, int caretOffset ) {
         _textToInsert = textToInsert;
         _caretOffset = caretOffset;
      }

   }
}
