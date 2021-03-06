package util.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.pool.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;


public class XMLHelper {

   private static Logger                 _log            = LoggerFactory.getLogger(XMLHelper.class);

   private static DocumentBuilderFactory _builderFactory = DocumentBuilderFactory.newInstance();


   public static XMLHelper newInstance() {
      return newInstance(_builderFactory);
   }

   public static XMLHelper newInstance( DocumentBuilderFactory factory ) {
      PoolDocumentBuilderFactory poolFactory = new PoolDocumentBuilderFactory(factory);
      XMLHelper ret = new XMLHelper();
      ret.setPoolFactory(poolFactory);
      return ret;
   }

   private static long copy( InputStream input, OutputStream output ) throws IOException {
      byte[] buffer = new byte[4096];
      long count = 0L;
      int n = 0;
      while ( -1 != (n = input.read(buffer)) ) {
         output.write(buffer, 0, n);
         count += n;
      }
      return count;
   }


   protected GenericObjectPool          _builderPool = new GenericObjectPool();

   protected PoolDocumentBuilderFactory _poolFactory;


   private XMLHelper() {}

   public Document parseXML( File inFile ) throws Exception {
      FileInputStream in = null;
      try {
         in = new FileInputStream(inFile);
         return parseXML(in);
      }
      finally {
         if ( in != null ) {
            in.close();
         }
      }

   }

   public Document parseXML( InputStream in ) throws Exception {
      DocumentBuilder builder = null;
      try {
         builder = (DocumentBuilder)_builderPool.borrowObject();
         Document dom = builder.parse(in);
         return dom;
      }
      catch ( SAXException e ) {
         if ( _log.isDebugEnabled() && in instanceof ByteArrayInputStream ) {
            ByteArrayInputStream inByte = (ByteArrayInputStream)in;
            inByte.reset();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(inByte, out);
            _log.debug(new String(out.toByteArray()));
         }
         throw e;
      }
      catch ( IOException e ) {
         throw e;
      }
      finally {
         if ( builder != null ) {
            _builderPool.returnObject(builder);
         }

      }
   }

   public Document parseXML( String xml, String encoding ) throws Exception {
      return parseXML(new ByteArrayInputStream(xml.getBytes(encoding)));
   }

   private void setPoolFactory( PoolDocumentBuilderFactory poolFactory ) {
      _builderPool.setFactory(poolFactory);
   }

}
