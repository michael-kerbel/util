package util.dump.stream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import javax.annotation.Nullable;

import util.io.IOUtils;


public enum Compression {
   None, GZipLevel0, GZipLevel1, GZipLevel2, GZipLevel3, GZipLevel4, GZipLevel5, GZipLevel6, GZipLevel7, GZipLevel8, GZipLevel9, Snappy;

   public byte[] compress( byte[] bytes, @Nullable byte[] target ) throws IOException {
      switch ( this ) {
      case None:
         return bytes;
      case GZipLevel0:
         return gzip(0, bytes);
      case GZipLevel1:
         return gzip(1, bytes);
      case GZipLevel2:
         return gzip(2, bytes);
      case GZipLevel3:
         return gzip(3, bytes);
      case GZipLevel4:
         return gzip(4, bytes);
      case GZipLevel5:
         return gzip(5, bytes);
      case GZipLevel6:
         return gzip(6, bytes);
      case GZipLevel7:
         return gzip(7, bytes);
      case GZipLevel8:
         return gzip(8, bytes);
      case GZipLevel9:
         return gzip(9, bytes);
      case Snappy:
         return snappy(bytes, target);
      }
      return bytes;
   }

   public byte[] uncompress( byte[] bytes, @Nullable byte[] target ) throws IOException {
      switch ( this ) {
      case None:
         return bytes;
      case GZipLevel0:
      case GZipLevel1:
      case GZipLevel2:
      case GZipLevel3:
      case GZipLevel4:
      case GZipLevel5:
      case GZipLevel6:
      case GZipLevel7:
      case GZipLevel8:
      case GZipLevel9:
         return gunzip(bytes);
      case Snappy:
         return unsnappy(bytes, target);

      }
      return bytes;
   }

   private byte[] gunzip( byte[] bytes ) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
      InputStream in = new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)), 8192);
      byte[] buffer = new byte[8192];
      int n = 0;
      while ( -1 != (n = in.read(buffer)) ) {
         out.write(buffer, 0, n);
      }
      in.close();
      out.close();
      return out.toByteArray();
   }

   private byte[] gzip( int compressionLevel, byte[] bytes ) throws IOException {
      ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
      ConfigurableGZIPOutputStream outputStream = new ConfigurableGZIPOutputStream(compressedBytes, compressionLevel);
      outputStream.write(bytes);
      IOUtils.close(outputStream);
      return compressedBytes.toByteArray();
   }

   private byte[] snappy( byte[] data, byte[] target ) {
      int length = org.iq80.snappy.Snappy.maxCompressedLength(data.length) + 4;
      if ( target == null || target.length < length ) {
         target = new byte[length];
      }
      int compressedSize = org.iq80.snappy.Snappy.compress(data, 0, data.length, target, 4);
      target[0] = (byte)((compressedSize >>> 24) & 0xFF);
      target[1] = (byte)((compressedSize >>> 16) & 0xFF);
      target[2] = (byte)((compressedSize >>> 8) & 0xFF);
      target[3] = (byte)((compressedSize >>> 0) & 0xFF);
      return target;
   }

   private byte[] unsnappy( byte[] bytes, byte[] target ) {
      int length = org.iq80.snappy.Snappy.getUncompressedLength(bytes, 0);
      if ( target == null || target.length < length ) {
         target = new byte[length];
      }
      org.iq80.snappy.Snappy.uncompress(bytes, 0, bytes.length, target, 0);
      return target;
   }
}
