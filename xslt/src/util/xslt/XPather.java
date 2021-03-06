package util.xslt;

import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import util.collections.LRUCache;


public class XPather {

   static final Processor                                  PROC                   = new Processor(false);
   static final ConcurrentHashMap<String, XPathExecutable> XPATH_EXECUTABLE_CACHE = new ConcurrentHashMap<>();
   static final Map<String, XdmNode>                       DOCUMENT_CACHE         = Collections.synchronizedMap(new LRUCache<>(30));

   public static String eval( String page, String xpath ) throws Exception {
      return eval(page, xpath, true);
   }

   public static String eval( String page, String xpath, boolean normalizeHtml ) throws Exception {

      XdmNode source = DOCUMENT_CACHE.get(page);
      if ( source == null ) {
         Document document;
         if ( normalizeHtml ) {
            CleanerProperties prop = new CleanerProperties();
            prop.setNamespacesAware(false);
            prop.setAllowHtmlInsideAttributes(true);
            HtmlCleaner cleaner = new HtmlCleaner(prop);
            TagNode clean = cleaner.clean(page);
            document = new LenientDomSerializer(prop).createDOM(clean);
         } else {
            document = loadXMLFromString(page);
         }
         source = PROC.newDocumentBuilder().build(new DOMSource(document));
         DOCUMENT_CACHE.put(page, source);
      }

      XPathExecutable xPathExecutable = XPATH_EXECUTABLE_CACHE.get(xpath);
      if ( xPathExecutable == null ) {
         XPathCompiler xPathCompiler = PROC.newXPathCompiler();
         xPathExecutable = xPathCompiler.compile(xpath);
         XPATH_EXECUTABLE_CACHE.put(xpath, xPathExecutable);
      }
      XPathSelector xPathSelector = xPathExecutable.load();
      xPathSelector.setContextItem(source);

      XdmValue value = xPathSelector.evaluate();
      if ( value.size() == 0 ) {
         return "";
      }
      if ( value.size() == 1 ) {
         return value.itemAt(0).getStringValue();
      }
      StringBuilder s = new StringBuilder();
      for ( int i = 0, length = value.size(); i < length; i++ ) {
         s.append(s.length() == 0 ? "" : "\n").append(value.itemAt(i).getStringValue());
      }
      return s.toString();
   }

   private static Document loadXMLFromString( String xml ) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();

      return builder.parse(new InputSource(new StringReader(xml)));
   }
}
