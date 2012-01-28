package util.xslt;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.DomSerializer;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;


public class Transformer {

   private static Logger                          _log               = Logger.getLogger(Transformer.class);

   private static final Pattern                   NON_BREAKING_SPACE = Pattern.compile("\\xa0");
   private static final Pattern                   SCRIPT_BLOCK       = Pattern.compile("(?s)<script>(.*?)</script>");

   private static final ThreadLocal<ScriptEngine> JAVASCRIPT_ENGINE  = new ThreadLocal<ScriptEngine>() {

                                                                        @Override
                                                                        protected ScriptEngine initialValue() {
                                                                           return new ScriptEngineManager().getEngineByName("JavaScript");
                                                                        }
                                                                     };


   public static XsltExecutable createXsltExecutable( String xslt, List<TransformationError> errors ) throws SaxonApiException {
      Processor proc = new Processor(false);
      XsltCompiler comp = proc.newXsltCompiler();
      ErrorListener errorListener = new ErrorCollector(errors);
      comp.setErrorListener(errorListener);
      XsltExecutable exp = comp.compile(new StreamSource(new StringReader(xslt)));
      return exp;
   }

   public static Map<String, String>[] toMap( String transformed ) {
      List<Map<String, String>> maps;
      try {
         maps = toMapLoud(transformed);
      }
      catch ( Exception argh ) {
         _log.debug("failed to parse XML", argh);
         maps = new ArrayList<Map<String, String>>();
      }
      return maps.toArray(new Map[maps.size()]);
   }

   /**
    * same as {@link #toMap(String)} only that it throws Exceptions
    */
   public static List<Map<String, String>> toMapLoud( String transformed ) throws Exception {
      transformed = NON_BREAKING_SPACE.matcher(transformed).replaceAll(" ");

      List<Map<String, String>> maps = new ArrayList<Map<String, String>>();
      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
      Document doc = docBuilder.parse(new InputSource(new StringReader(transformed)));
      doc.getDocumentElement().normalize();

      Node root = doc.getChildNodes().item(0);
      NodeList rootElements = root.getChildNodes();
      for ( int i = 0; i < rootElements.getLength(); i++ ) {
         NodeList childNodes = rootElements.item(i).getChildNodes();
         if ( childNodes.getLength() == 0 ) {
            continue;
         }
         Map<String, String> map = new HashMap<String, String>();
         addChildren(map, childNodes);
         maps.add(map);
      }

      return maps;
   }

   public static TransformationResult transform( String page, String xslt, Map<String, String> variablesForXSLT ) {
      List<TransformationError> errors = new ArrayList<TransformationError>();
      XsltExecutable exp;
      try {
         exp = createXsltExecutable(xslt, errors);
      }
      catch ( SaxonApiException argh ) {
         TransformationResult r = new TransformationResult();
         r._errors = errors;
         return r;
      }
      return transform(page, xslt, exp, variablesForXSLT);
   }

   public static TransformationResult transform( String url, String linklabel, String page, String xslt ) {
      Map<String, String> variablesForXSLT = new HashMap<String, String>();
      variablesForXSLT.put("url", url);
      variablesForXSLT.put("linklabel", linklabel);
      return transform(page, xslt, variablesForXSLT);
   }

   public static TransformationResult transform( String page, String xslt, XsltExecutable exp, Map<String, String> variablesForXSLT ) {
      StringWriter sw;
      final TransformationResult r = new TransformationResult();
      try {
         Processor proc = new Processor(false);

         if ( isNotHtml(page) ) {
            // script tag preserves the text as text in the DOM, i.e. it doesn't try to parse any html elements which might occur in the text 
            page = "<script>" + page + "</script>";
         }
         HtmlCleaner cleaner = new HtmlCleaner();
         TagNode clean = cleaner.clean(page);
         Document document = new DomSerializer(new CleanerProperties()).createDOM(clean);

         XdmNode source = proc.newDocumentBuilder().build(new DOMSource(document));

         sw = new StringWriter();
         Serializer out = new Serializer();
         out.setOutputWriter(sw);
         XsltTransformer trans = exp.load();

         for ( Map.Entry<String, String> e : variablesForXSLT.entrySet() ) {
            String variableName = e.getKey();
            String value = e.getValue();
            trans.setParameter(new QName(variableName), new XdmValue((Iterable)Collections.singletonList(new XdmAtomicValue(value))));
         }
         trans.setParameter(new QName("newline"), new XdmValue((Iterable)Collections.singletonList(new XdmAtomicValue("\n"))));

         trans.setInitialContextNode(source);
         trans.setDestination(out);
         ErrorListener errorListener = new ErrorCollector(r._errors);
         trans.getUnderlyingController().setErrorListener(errorListener);
         trans.transform();
         r._result = sw.toString();
         replaceJavaScriptBlocks(xslt, r);
      }
      catch ( Exception argh ) {
         _log.warn("Failed to execute xslt", argh);
      }
      return r;
   }

   private static void addChildren( Map<String, String> map, NodeList childNodes ) {
      for ( int j = 0; j < childNodes.getLength(); j++ ) {
         Node item = childNodes.item(j);
         String nodeName = item.getNodeName();
         String text = item.getTextContent();
         map.put(nodeName, text);
         NodeList childChildNodes = item.getChildNodes();
         if ( childChildNodes != null && childChildNodes.getLength() > 0 ) {
            addChildren(map, childChildNodes);
         }
      }
   }

   private static String evaluateJavaScript( String js, String xslt, TransformationResult r, int n, Map<String, String>[] maps ) {
      StringWriter out = new StringWriter();
      try {
         ScriptEngine scriptEngine = JAVASCRIPT_ENGINE.get();
         scriptEngine.getContext().setAttribute("elem", maps, ScriptContext.ENGINE_SCOPE);
         scriptEngine.getContext().setWriter(new PrintWriter(out));
         scriptEngine.eval(js);
      }
      catch ( ScriptException argh ) {
         int jsIndex = StringUtils.ordinalIndexOf(xslt, "<script>", n);
         if ( jsIndex >= 0 ) {
            int line = StringUtils.countMatches(xslt.substring(0, jsIndex), "\n") + 1;
            r._errors.add(new TransformationError(argh.getMessage(), line, 0));
            _log.debug("Failed to execute js:\n" + js);
         } else {
            _log.warn("Failed to execute js:\n" + js);
         }
      }
      return out.toString().trim();
   }

   private static boolean isNotHtml( String page ) {
      page = page.toLowerCase();
      return !page.trim().startsWith("<") && !page.contains("<!DOCTYPE") && !page.contains("<html") && !page.contains("<head") && !page.contains("<body");
   }

   private static void replaceJavaScriptBlocks( String xslt, TransformationResult r ) {
      Matcher matcher = SCRIPT_BLOCK.matcher(r._result);
      boolean result = matcher.find();
      int n = 0;
      if ( result ) {
         Map<String, String>[] maps = toMap(r._result);

         StringBuffer sb = new StringBuffer();
         do {
            String js = matcher.group(1);
            js = StringUtils.replaceEach(js, new String[] { "&amp;", "&lt;", "&gt;" }, new String[] { "&", "<", ">" });
            String replacement = evaluateJavaScript(js, xslt, r, ++n, maps);
            replacement = StringUtils.replaceEach(replacement, new String[] { "\\", "$" }, new String[] { "\\\\", "\\$" });
            matcher.appendReplacement(sb, replacement);
            result = matcher.find();
         }
         while ( result );
         matcher.appendTail(sb);
         r._result = sb.toString();
      }
   }


   public static class TransformationError {

      public String _error;
      public int    _lineNumber;
      public int    _columnNumber;


      public TransformationError( String error, int lineNumber, int columnNumber ) {
         _error = error;
         _lineNumber = lineNumber;
         _columnNumber = columnNumber;
      }
   }

   public static class TransformationResult {

      public String                    _result = "";
      public List<TransformationError> _errors = new ArrayList<TransformationError>();
   }

   private static final class ErrorCollector implements ErrorListener {

      List<TransformationError> _errors;


      public ErrorCollector( List<TransformationError> errors ) {
         _errors = errors;
      }

      @Override
      public void error( TransformerException exception ) throws TransformerException {
         addError(exception);
      }

      @Override
      public void fatalError( TransformerException exception ) throws TransformerException {
         addError(exception);
      }

      @Override
      public void warning( TransformerException exception ) throws TransformerException {
         addError(exception);
      }

      private void addError( TransformerException exception ) {
         String error = exception.getMessage();
         SourceLocator loc = exception.getLocator();
         int lineNumber = 0, columnNumber = 0;
         if ( loc != null ) {
            lineNumber = loc.getLineNumber();
            columnNumber = loc.getColumnNumber();
         } else {
            if ( exception.getException() instanceof SAXParseException ) {
               SAXParseException sex = (SAXParseException)exception.getException();
               lineNumber = sex.getLineNumber();
               columnNumber = sex.getColumnNumber();
            }
         }
         _errors.add(new TransformationError(error, lineNumber, columnNumber));
      }
   }
}
