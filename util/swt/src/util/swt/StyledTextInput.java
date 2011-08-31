package util.swt;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;

import util.xml.StaxReader;


public class StyledTextInput {

   public static void main( String[] args ) throws Exception {
      StyledTextInput sti = new StyledTextInput(
         "Normal <b>bold</b> <i>italic</i> <del>strikethrough</del> normal <b><font color='#555555' size='12' face='Courier New'>red big bold Courier</font></b>");
      System.err.println(sti._text);
      for ( int i = 0, length = sti._styleRanges.length; i < length; i++ ) {
         System.err.println(sti._styleRanges[i]);
      }
   }


   String           _html;
   StyleRange[]     _styleRanges;
   String           _text;

   StringBuilder    _sb   = new StringBuilder();
   List<StyleRange> _list = new ArrayList<StyleRange>();


   public StyledTextInput( String html ) throws Exception {
      _html = html;
      _list.add(new StyleRange());
      XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader("<root>" + html + "</root>"));
      new HTMLReader(reader);
      endLastAndCreateNewStyle();
      _list.remove(_list.size() - 1);
      _styleRanges = _list.toArray(new StyleRange[_list.size()]);
      _text = _sb.toString();
      _sb.setLength(0);
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
      StyledTextInput other = (StyledTextInput)obj;
      if ( _html == null ) {
         if ( other._html != null ) {
            return false;
         }
      } else if ( !_html.equals(other._html) ) {
         return false;
      }
      return true;
   }

   public StyleRange[] getStyleRanges() {
      return _styleRanges;
   }

   public String getText() {
      return _text;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((_html == null) ? 0 : _html.hashCode());
      return result;
   }

   private StyleRange endLastAndCreateNewStyle() {
      StyleRange style = _list.get(_list.size() - 1);
      style.length = _sb.length() - style.start;
      if ( style.length > 0 ) {
         style = new StyleRange(style);
         style.start = _sb.length();
         _list.add(style);
      }
      return style;
   }


   private class HTMLReader extends StaxReader {

      public HTMLReader( XMLStreamReader reader ) throws XMLStreamException {
         super(reader);
      }

      @Override
      protected void characters( XMLStreamReader reader ) {
         String text = reader.getText();
         _sb.append(text);
      }

      @Override
      protected void endElement( XMLStreamReader reader ) {
         String tag = reader.getName().getLocalPart();
         if ( tag.equalsIgnoreCase("b") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.fontStyle &= ~SWT.BOLD;
         } else if ( tag.equalsIgnoreCase("i") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.fontStyle &= ~SWT.ITALIC;
         } else if ( tag.equalsIgnoreCase("u") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.underline = false;
         } else if ( tag.equalsIgnoreCase("del") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.strikeout = false;
         } else if ( tag.equalsIgnoreCase("font") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.foreground = null;
            style.font = null;
         } else if ( tag.equalsIgnoreCase("br") ) {
            _sb.append("\n");
         }
      }

      @Override
      protected void startElement( XMLStreamReader reader ) {
         String tag = reader.getName().getLocalPart();
         if ( tag.equalsIgnoreCase("b") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.fontStyle |= SWT.BOLD;
         } else if ( tag.equalsIgnoreCase("font") ) {
            int attributeCount = reader.getAttributeCount();

            Color color = null;
            int size = -1;
            String face = null;
            for ( int i = 0; i < attributeCount; i++ ) {
               if ( reader.getAttributeName(i).getLocalPart().equalsIgnoreCase("color") ) {
                  color = getColorFromString(reader.getAttributeValue(i));
               } else if ( reader.getAttributeName(i).getLocalPart().equalsIgnoreCase("size") ) {
                  size = Integer.parseInt(reader.getAttributeValue(i));
               } else if ( reader.getAttributeName(i).getLocalPart().equalsIgnoreCase("face") ) {
                  face = reader.getAttributeValue(i);
               }
            }

            if ( color != null || size > 0 || face != null ) {
               StyleRange style = endLastAndCreateNewStyle();
               style.foreground = color;
               if ( size < 0 ) {
                  size = ResourceManager.getSystemFont().getFontData()[0].getHeight();
               }
               if ( face == null ) {
                  style.font = ResourceManager.getFont(ResourceManager.getSystemFont(), size, style.fontStyle);
               } else {
                  style.font = ResourceManager.getFont(face, size, style.fontStyle);
               }
            }
         } else if ( tag.equalsIgnoreCase("i") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.fontStyle |= SWT.ITALIC;
         } else if ( tag.equalsIgnoreCase("u") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.underline = false;
         } else if ( tag.equalsIgnoreCase("del") ) {
            StyleRange style = endLastAndCreateNewStyle();
            style.strikeout = false;
         }
      }

      private Color getColorFromString( String s ) {
         if ( s.charAt(0) == '#' ) {
            s = s.substring(1);
         }
         int r = Integer.parseInt(s.substring(0, 2), 16);
         int g = Integer.parseInt(s.substring(2, 4), 16);
         int b = Integer.parseInt(s.substring(4, 6), 16);
         Color color = ResourceManager.getColor(r, g, b);
         return color;
      }
   }
}
