package util.ssh;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import util.string.StringListener;
import util.time.StopWatch;
import util.time.TimeUtils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;


public class SshHelper {

   private static Logger        _log                             = Logger.getLogger(SshHelper.class);

   private static final Pattern TERMINAL_EMULATION_CHARS_PATTERN = Pattern.compile("\u001b\\[[0-9;?]*[^0-9;]");


   public static String cleanFromTerminalEmulationChars( String s ) {
      s = s.replace("\u001b(B\u001b)0", "");
      s = TERMINAL_EMULATION_CHARS_PATTERN.matcher(s).replaceAll("");
      s = s.replace("\u000f", "");
      s = s.replace("\u001b=*", "");
      return s;
   }

   public static void exec( Session session, StringListener lineListener, String[]... commands ) throws Exception {
      Channel channel = session.openChannel("shell");
      //((ChannelShell)channel).setPtyType("ansi");

      BufferedReader fromServer = new BufferedReader(new InputStreamReader(channel.getInputStream()));
      OutputStreamWriter toServer = new OutputStreamWriter(channel.getOutputStream());

      connect(channel, lineListener);

      for ( String[] commandAndWait : commands ) {
         toServer.write(commandAndWait[0] + "\n");
         toServer.flush();
         if ( commandAndWait.length > 1 && commandAndWait[1] != null ) {
            for ( String line = fromServer.readLine(); line != null; line = fromServer.readLine() ) {
               if ( lineListener != null ) lineListener.add(cleanFromTerminalEmulationChars(line) + "\n");
               if ( line.contains(commandAndWait[1]) ) break;
            }
         }
      }

      channel.disconnect();
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

         connect(channel, lineListener);

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

   static int checkAck( InputStream in ) throws IOException {
      int b = in.read();
      // b may be 0 for success,
      //          1 for error,
      //          2 for fatal error,
      //          -1
      if ( b == 0 ) return b;
      if ( b == -1 ) return b;

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

   static void connect( Channel channel, StringListener lineListener ) {
      int retries = 3;
      int timeoutInSeconds = 5;
      while ( retries > 0 ) {
         try {
            channel.connect(timeoutInSeconds * 1000);
            if ( lineListener != null ) lineListener.add("INFO   connected\n");
            break;
         }
         catch ( Exception argh ) {
            retries--;
            timeoutInSeconds += 2;
            if ( retries > 0 ) {
               _log.warn("Failed to connect: " + argh.getMessage() + " - retrying");
               if ( lineListener != null ) lineListener.add("WARN Failed to connect: " + argh.getMessage() + " - retrying\n");
            }
         }
      }
      if ( !channel.isConnected() ) throw new RuntimeException("Failed to connect.");
   }

   private static long copy( InputStream input, OutputStream output, long totalLength, StringListener lineListener ) throws IOException {
      StopWatch t = new StopWatch();

      byte[] buffer = new byte[1024 * 4];
      long count = 0;
      int n = 0;
      int last1MBChunk = 0;
      String lastMessage = "";
      while ( -1 != (n = input.read(buffer)) ) {
         output.write(buffer, 0, n);
         count += n;

         int current1MBChunk = (int)(count / (1024 * 1024));
         if ( current1MBChunk > last1MBChunk ) {
            int kbS = (int)(((float)count / t.getInterval()) / 1024 * 1000);
            long eta = (long)((t.getInterval() / (float)count * totalLength) - t.getInterval());

            if ( lineListener != null ) lineListener.add(StringUtils.repeat("\b", lastMessage.length()));
            lastMessage = "INFO   copied " + ((int)count / (1024 * 1024)) + " MB of " + ((int)totalLength / (1024 * 1024)) + " MB. " + kbS + " kb/s, ETA: "
               + TimeUtils.toHumanReadableFormat(eta);
            if ( lineListener != null ) lineListener.add(lastMessage);

            last1MBChunk = current1MBChunk;
         }
      }
      if ( last1MBChunk > 0 && lineListener != null ) lineListener.add("\n");
      return count;
   }
}
