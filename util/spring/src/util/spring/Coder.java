package util.spring;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;


public class Coder {

   public static String decode( String s ) {
      if ( s == null || s.length() == 0 ) throw new IllegalArgumentException("string must not be empty");
      if ( s.length() % 4 != 0 ) throw new IllegalArgumentException("invalid string");

      byte[] bytes = new byte[s.length() / 4];

      for ( int i = 0; i < s.length(); i += 4 ) {
         String v = s.substring(i, i + 2);
         String c = s.substring(i + 2, i + 4);
         bytes[i >> 2] = (byte)(decodeByte(v) ^ decodeByte(c));
         if ( i > 0 ) bytes[i >> 2] -= bytes[(i - 1) >> 2];
      }

      try {
         return new String(bytes, "UTF-8");
      }
      catch ( UnsupportedEncodingException e ) {
         throw new RuntimeException("Encoding 'UTF-8' is unknown, this is not a java virtual machine :)");
      }
   }

   public static String encode( String s ) {
      if ( s == null || s.length() == 0 ) throw new IllegalArgumentException("string must not be empty");

      SecureRandom random = new SecureRandom();

      try {
         byte[] bytes = s.getBytes("UTF-8");

         byte[] salt = new byte[bytes.length];
         random.nextBytes(salt);

         StringBuilder sb = new StringBuilder();

         for ( int i = 0, length = bytes.length; i < length; i++ ) {
            byte b = bytes[i];
            byte c = salt[i];
            if ( i > 0 ) b += bytes[i - 1];
            b = (byte)(b ^ c);
            sb.append(encode(b)).append(encode(c));
         }

         String result = sb.toString();
         if ( result.length() != bytes.length * 4 ) {
            throw new IllegalStateException("weird. encoding failed (unexpected result length)");
         }
         return result;
      }
      catch ( UnsupportedEncodingException e ) {
         throw new RuntimeException("Encoding 'UTF-8' is unknown, this is not a java virtual machine :)");
      }
   }

   public static void main( String[] args ) {
      if ( args == null || args.length < 1 ) {
         System.out.println("Please specify your password as program argument.");
      } else {
         System.err.println(encode(args[0]));
      }
   }

   private static int decodeByte( String v ) {
      return (Integer.parseInt(v, 36) - 1) / 3;
   }

   private static String encode( byte b ) {
      String string = Integer.toString((b & 0xff) * 3 + 1, 36);
      return (string.length() == 1) ? "0" + string : string;
   }

}
