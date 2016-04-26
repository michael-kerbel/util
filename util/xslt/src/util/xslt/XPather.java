package util.xslt;

import javax.xml.transform.dom.DOMSource;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.DomSerializer;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.w3c.dom.Document;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;


public class XPather {

   public static String eval( String page, String xpath ) throws Exception {
      Processor proc = new Processor(false);

      CleanerProperties prop = new CleanerProperties();
      prop.setNamespacesAware(false);
      HtmlCleaner cleaner = new HtmlCleaner(prop);
      TagNode clean = cleaner.clean(page);
      Document document = new DomSerializer(prop).createDOM(clean);

      XdmNode source = proc.newDocumentBuilder().build(new DOMSource(document));

      XPathCompiler xPathCompiler = proc.newXPathCompiler();
      XPathExecutable xPathExecutable = xPathCompiler.compile(xpath);
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
}
