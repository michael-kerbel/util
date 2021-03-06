package util.ssh;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import util.string.StringListener;
import util.time.StopWatch;
import util.time.TimeUtils;


/**
 * These are basically the examples from http://www.jcraft.com/jsch/examples/ put into a util class
 */
public class SshHelper {

   public static int TIMEOUT_IN_SECONDS = 10;

   private static Logger _log = LoggerFactory.getLogger(SshHelper.class);

   private static final Pattern TERMINAL_EMULATION_CHARS_PATTERN = Pattern.compile("\u001b\\[[0-9;?]*[^0-9;]");

   public static String cleanFromTerminalEmulationChars( String s ) {
      s = s.replace("\u001b(B\u001b)0", "");
      s = TERMINAL_EMULATION_CHARS_PATTERN.matcher(s).replaceAll("");
      s = s.replace("\u000f", "");
      s = s.replace("\u001b=*", "");
      return s;
   }

   public static void exec( Session session, StringListener lineListener, Pattern abortPattern, String[]... commands ) throws Exception {
      Channel channel = session.openChannel("shell");
      //((ChannelShell)channel).setPtyType("ansi");

      BufferedReader fromServer = new BufferedReader(new InputStreamReader(channel.getInputStream()));
      OutputStreamWriter toServer = new OutputStreamWriter(channel.getOutputStream());

      connect(channel, lineListener, TIMEOUT_IN_SECONDS);

      try {
         for ( String[] commandAndWait : commands ) {
            toServer.write(commandAndWait[0] + "\n");
            toServer.flush();
            if ( commandAndWait.length > 1 && commandAndWait[1] != null ) {
               for ( String line = fromServer.readLine(); line != null; line = fromServer.readLine() ) {
                  if ( lineListener != null ) {
                     lineListener.add(cleanFromTerminalEmulationChars(line) + "\n");
                  }
                  if ( line.contains(commandAndWait[1]) ) {
                     break;
                  }
                  if ( abortPattern != null && abortPattern.matcher(line).find() ) {
                     throw new IllegalStateException("Server log matched abort reg-ex: " + line);
                  }
               }
            }
         }
      }
      finally {
         channel.disconnect();
      }
   }

   public static Session openSshSession( String host, String user, byte[] prvKey, byte[] pubKey ) throws Exception {
      JSch jsch = new JSch();
      Session session = jsch.getSession(user, host, 22);
      Properties config = new Properties();
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);
      jsch.addIdentity(null, prvKey, pubKey, null);
      session.connect(30000);
      return session;
   }

   public static Session openSshSession( String host, String user, String password ) throws Exception {
      JSch jsch = new JSch();
      Session session = jsch.getSession(user, host, 22);

      Properties config = new Properties();
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);

      session.setPassword(password);
      session.connect(30000);
      return session;
   }

   public static void scp( Session session, File localFile, String remoteFile, StringListener lineListener ) throws Exception {
      FileInputStream fis = null;
      try {
         // exec 'scp -t rfile' remotely
         String command = "scp -p -t " + remoteFile;
         Channel channel = session.openChannel("exec");
         ((ChannelExec)channel).setCommand(command);

         // get I/O streams for remote scp
         OutputStream out = channel.getOutputStream();
         InputStream in = channel.getInputStream();

         connect(channel, lineListener, TIMEOUT_IN_SECONDS);

         if ( checkAck(in) != 0 ) {
            throw new RuntimeException("Scp ack!=0 after connect");
         }

         // send "C0644 filesize filename", where filename should not include '/'
         command = "C0644 " + localFile.length() + " " + localFile.getName() + "\n";
         out.write(command.getBytes());
         out.flush();
         if ( checkAck(in) != 0 ) {
            throw new RuntimeException("Scp ack!=0 after command");
         }

         // send a content of lfile
         fis = new FileInputStream(localFile);
         copy(fis, out, localFile.length(), lineListener);
         fis.close();
         // send '\0'
         out.write(0);
         out.flush();
         if ( checkAck(in) != 0 ) {
            throw new RuntimeException("Scp ack!=0 after file was sent");
         }
         out.close();

         channel.disconnect();
      }
      finally {
         IOUtils.closeQuietly(fis);
      }
   }

   public static void scp( Session session, String remoteFile, File localDir, StringListener lineListener ) throws Exception {
      FileOutputStream fos = null;
      try {
         // exec 'scp -f remoteFile' remotely
         String command = "scp -f " + remoteFile;
         Channel channel = session.openChannel("exec");
         ((ChannelExec)channel).setCommand(command);

         // get I/O streams for remote scp
         OutputStream out = channel.getOutputStream();
         InputStream in = channel.getInputStream();

         connect(channel, lineListener, TIMEOUT_IN_SECONDS);

         byte[] buf = new byte[1024];

         // send '\0'
         buf[0] = 0;
         out.write(buf, 0, 1);
         out.flush();

         while ( true ) {
            int c = checkAck(in);
            if ( c != 'C' ) {
               break;
            }

            // read '0644 '
            in.read(buf, 0, 5);

            long filesize = 0L;
            while ( true ) {
               if ( in.read(buf, 0, 1) < 0 ) {
                  // error
                  break;
               }
               if ( buf[0] == ' ' ) {
                  break;
               }
               filesize = filesize * 10L + buf[0] - '0';
            }

            String file = null;
            for ( int i = 0; ; i++ ) {
               in.read(buf, i, 1);
               if ( buf[i] == (byte)0x0a ) {
                  file = new String(buf, 0, i);
                  break;
               }
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();

            // read a content of lfile
            fos = new FileOutputStream(localDir.getAbsolutePath() + "/" + file);
            copy(in, fos, filesize, lineListener);
            fos.close();
            fos = null;

            if ( checkAck(in) != 0 ) {
               return;
            }

            // send '\0'
            buf[0] = 0;
            out.write(buf, 0, 1);
            out.flush();
         }
         channel.disconnect();
      }
      finally {
         IOUtils.closeQuietly(fos);
      }
   }

   static int checkAck( InputStream in ) throws IOException {
      int b = in.read();
      // b may be 0 for success,
      //          1 for error,
      //          2 for fatal error,
      //          -1
      if ( b == 0 ) {
         return b;
      }
      if ( b == -1 ) {
         return b;
      }

      if ( b == 1 || b == 2 ) {
         StringBuffer sb = new StringBuffer();
         int c;
         do {
            c = in.read();
            sb.append((char)c);
         }
         while ( c != '\n' );
         if ( b == 1 ) { // error
            System.out.print(sb.toString());
         }
         if ( b == 2 ) { // fatal error
            System.out.print(sb.toString());
         }
      }
      return b;
   }

   static void connect( Channel channel, StringListener lineListener, int timeoutInSeconds ) {
      int retries = 3;
      while ( retries > 0 ) {
         try {
            channel.connect(timeoutInSeconds * 1000);
            if ( lineListener != null ) {
               lineListener.add("INFO   connected\n");
            }
            break;
         }
         catch ( Exception argh ) {
            retries--;
            if ( retries > 0 ) {
               _log.warn("Failed to connect: " + argh.getMessage() + " - retrying");
               if ( lineListener != null ) {
                  lineListener.add("WARN Failed to connect: " + argh.getMessage() + " - retrying\n");
               }
            }
         }
      }
      if ( !channel.isConnected() ) {
         throw new RuntimeException("Failed to connect.");
      }
   }

   private static long copy( InputStream input, OutputStream output, long totalLength, StringListener lineListener ) throws IOException {
      StopWatch t = new StopWatch();

      byte[] buffer = new byte[1024 * 4];
      long count = 0;
      int n = 0;
      int last1MBChunk = 0;
      String lastMessage = "";
      while ( -1 != (n = input.read(buffer, 0, Math.min((int)(totalLength - count), buffer.length))) ) {
         output.write(buffer, 0, n);
         count += n;
         if ( count >= totalLength ) {
            break;
         }

         int current1MBChunk = (int)(count / (1024 * 1024));
         if ( current1MBChunk > last1MBChunk ) {
            int kbS = (int)(((float)count / t.getInterval()) / 1024 * 1000);
            long eta = (long)((t.getInterval() / (float)count * totalLength) - t.getInterval());

            if ( lineListener != null ) {
               lineListener.add(StringUtils.repeat("\b", lastMessage.length()));
            }
            lastMessage = "INFO   copied " + (count / (1024 * 1024)) + " MB of " + (totalLength / (1024 * 1024)) + " MB. " + kbS + " kb/s, ETA: "
                  + TimeUtils.toHumanReadableFormat(eta);
            if ( lineListener != null ) {
               lineListener.add(lastMessage);
            }

            last1MBChunk = current1MBChunk;
         }
      }
      if ( last1MBChunk > 0 && lineListener != null ) {
         lineListener.add("\n");
      }
      return count;
   }
}
