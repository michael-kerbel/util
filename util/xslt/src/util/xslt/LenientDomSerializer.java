package util.xslt;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.CommentToken;
import org.htmlcleaner.ContentToken;
import org.htmlcleaner.TagNode;
import org.htmlcleaner.Utils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * same as org.htmlcleaner.DomSerializer, but without error checking
 */
public class LenientDomSerializer {

   protected CleanerProperties props;
   protected boolean           escapeXml = true;


   public LenientDomSerializer( CleanerProperties paramCleanerProperties, boolean paramBoolean ) {
      props = paramCleanerProperties;
      escapeXml = paramBoolean;
   }

   public LenientDomSerializer( CleanerProperties paramCleanerProperties ) {
      this(paramCleanerProperties, true);
   }

   public Document createDOM( TagNode paramTagNode ) throws ParserConfigurationException {
      DocumentBuilderFactory localDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
      Document localDocument = localDocumentBuilderFactory.newDocumentBuilder().newDocument();

      /* only the following line was added compared to org.htmlcleaner.DomSerializer */
      localDocument.setStrictErrorChecking(false);

      Element localElement = localDocument.createElement(paramTagNode.getName());
      localDocument.appendChild(localElement);
      createSubnodes(localDocument, localElement, paramTagNode.getChildren());
      return localDocument;
   }

   private void createSubnodes( Document paramDocument, Element paramElement, List paramList ) {
      if ( paramList != null ) {
         Iterator localIterator1 = paramList.iterator();
         while ( localIterator1.hasNext() ) {
            Object localObject1 = localIterator1.next();
            Object localObject2;
            Object localObject3;
            if ( (localObject1 instanceof CommentToken) ) {
               localObject2 = localObject1;
               localObject3 = paramDocument.createComment(((CommentToken)localObject2).getContent());
               paramElement.appendChild((Node)localObject3);
            } else {
               Object localObject4;
               if ( (localObject1 instanceof ContentToken) ) {
                  localObject2 = paramElement.getNodeName();
                  localObject3 = localObject1;
                  localObject4 = ((ContentToken)localObject3).getContent();
                  int i = (props.isUseCdataForScriptAndStyle())
                     && (("script".equalsIgnoreCase((String)localObject2)) || ("style".equalsIgnoreCase((String)localObject2))) ? 1 : 0;
                  if ( (escapeXml) && (i == 0) ) {
                     localObject4 = Utils.escapeXml((String)localObject4, props, true);
                  }
                  paramElement
                        .appendChild(i != 0 ? paramDocument.createCDATASection((String)localObject4) : paramDocument.createTextNode((String)localObject4));
               } else if ( (localObject1 instanceof TagNode) ) {
                  localObject2 = localObject1;
                  localObject3 = paramDocument.createElement(((TagNode)localObject2).getName());
                  localObject4 = ((TagNode)localObject2).getAttributes();
                  Iterator localIterator2 = ((Map)localObject4).entrySet().iterator();
                  while ( localIterator2.hasNext() ) {
                     Map.Entry localEntry = (Map.Entry)localIterator2.next();
                     String str1 = (String)localEntry.getKey();
                     String str2 = (String)localEntry.getValue();
                     if ( escapeXml ) {
                        str2 = Utils.escapeXml(str2, props, true);
                     }
                     ((Element)localObject3).setAttribute(str1, str2);
                  }
                  createSubnodes(paramDocument, (Element)localObject3, ((TagNode)localObject2).getChildren());
                  paramElement.appendChild((Node)localObject3);
               } else if ( (localObject1 instanceof List) ) {
                  localObject2 = localObject1;
                  createSubnodes(paramDocument, paramElement, (List)localObject2);
               }
            }
         }
      }
   }
}

/* Location:           C:\Dev\maven-repository\org\htmlcleaner\htmlcleaner\2.1\htmlcleaner-2.1.jar
 * Qualified Name:     org.htmlcleaner.DomSerializer
 * Java Class Version: 1.4 (48.0)
 * JD-Core Version:    0.7.1
 */