package util.crawler.values;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import util.dump.Externalizer;


public class GzippedHtml extends Externalizer {

   private static Logger _log = Logger.getLogger(GzippedHtml.class);

   @externalize(1)
   String                _url;
   String                _page;


   public GzippedHtml() {}

   public GzippedHtml( String url, String page ) {
      _url = url;
      _page = page;
   }

   public String getPage() {
      return _page;
   }

   @externalize(2)
   public byte[] getPageBytes() {
      BufferedOutputStream out = null;
      try {
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         out = new BufferedOutputStream(new GZIPOutputStream(bytes));
         out.write(_page.getBytes("UTF-8"));
         IOUtils.closeQuietly(out);
         return bytes.toByteArray();
      }
      catch ( Exception argh ) {
         _log.error("Failed to compress html", argh);
         return null;
      }
   }

   public String getUrl() {
      return _url;
   }

   public void setPageBytes( byte[] b ) {
      InputStream in = null;
      try {
         in = new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(b)));
         _page = new String(IOUtils.toByteArray(in), "UTF-8");
      }
      catch ( Exception argh ) {
         _log.error("Failed to decompress html", argh);
         // ignore, does not happen
      }
      finally {
         IOUtils.closeQuietly(in);
      }
   }
}