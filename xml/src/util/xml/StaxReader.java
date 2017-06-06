package util.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;


/**
 * Nice read: http://today.java.net/pub/a/today/2006/07/20/introduction-to-stax.html
 */
public class StaxReader {

   public StaxReader( XMLStreamReader reader ) throws XMLStreamException {
      read(reader);
   }

   protected void cdata( XMLStreamReader reader ) {}

   protected void characters( XMLStreamReader reader ) {}

   protected void comment( XMLStreamReader reader ) {}

   protected void dtd( XMLStreamReader reader ) {}

   protected void endDocument( XMLStreamReader reader ) {}

   protected void endElement( XMLStreamReader reader ) {}

   protected void entityReference( XMLStreamReader reader ) {}

   protected void processingInstruction( XMLStreamReader reader ) {}

   protected void read( XMLStreamReader reader ) throws XMLStreamException {
      while ( reader.hasNext() ) {
         int eventCode = reader.next();
         switch ( eventCode ) {
         case XMLStreamConstants.START_ELEMENT:
            startElement(reader);
            break;
         case XMLStreamConstants.END_ELEMENT:
            endElement(reader);
            break;
         case XMLStreamConstants.PROCESSING_INSTRUCTION:
            processingInstruction(reader);
            break;
         case XMLStreamConstants.CHARACTERS:
            characters(reader);
            break;
         case XMLStreamConstants.COMMENT:
            comment(reader);
            break;
         case XMLStreamConstants.SPACE:
            space(reader);
            break;
         case XMLStreamConstants.START_DOCUMENT:
            startDocument(reader);
            break;
         case XMLStreamConstants.END_DOCUMENT:
            endDocument(reader);
            break;
         case XMLStreamConstants.ENTITY_REFERENCE:
            entityReference(reader);
            break;
         case XMLStreamConstants.DTD:
            dtd(reader);
            break;
         case XMLStreamConstants.CDATA:
            cdata(reader);
            break;
         }
      }
      reader.close();
   }

   protected void space( XMLStreamReader reader ) {}

   protected void startDocument( XMLStreamReader reader ) {}

   protected void startElement( XMLStreamReader reader ) {}
}
