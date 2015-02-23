package util.crawler.proxy;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.spring.Coder;


public class ProxyListImporter {

   private static final String LAST_MESSAGE_ID_FILENAME = "proxylist-last-message-id";
   private static Logger _log = LoggerFactory.getLogger(ProxyListImporter.class);


   public static void main( String[] args ) {
      try {
         new ProxyListImporter().doIt();
      }
      catch ( Exception argh ) {
         _log.error("Failed.", argh);
      }
   }


   String  _host          = "pop.1und1.de";
   String  _username      = "proxylist@chrono24.net";
   String  _password      = Coder.decode("9p2g24av2dav5vkde444dv4gj4cjbd1s3md7f10jip4m8sgm6sivaddg");
   Session _session;

   int     _lastMessageId = 1;

   String  _proxyListFilename;


   public ProxyListImporter() throws IOException {
      _proxyListFilename = System.getProperty("proxyListFilename", "proxies.txt");

      Properties props = new Properties();
      props.setProperty("mail.pop3.host", _host);
      props.setProperty("mail.pop3.starttls.enable", "true"); // for this to work we need at least javax.mail 1.4.3 !
      props.setProperty("mail.pop3.starttls.required", "true");

      _session = Session.getInstance(props, new Authenticator() {

         @Override
         protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(_username, _password);
         }
      });

      File lastMessageId = new File(LAST_MESSAGE_ID_FILENAME);
      if ( lastMessageId.exists() ) {
         String s = FileUtils.readFileToString(lastMessageId);
         _lastMessageId = Integer.parseInt(s.trim());
      }
   }

   public void doIt() throws Exception {
      Store store = _session.getStore("pop3");
      store.connect();

      Folder inbox = store.getFolder("INBOX");
      if ( inbox == null ) {
         _log.error("No INBOX");
         System.exit(1);
      }
      inbox.open(Folder.READ_ONLY);

      int messageCount = inbox.getMessageCount();
      try {
         Message[] messages = inbox.getMessages(_lastMessageId, messageCount);
         messages = inbox.getMessages();
         Arrays.sort(messages, new Comparator<Message>() {

            @Override
            public int compare( Message m1, Message m2 ) {
               try {
                  return -m1.getSentDate().compareTo(m2.getSentDate());
               }
               catch ( MessagingException argh ) {
                  return 0;
               }
            }
         });

         for ( int i = 0; i < messages.length; i++ ) {
            _log.debug("Message " + messages[i].getSubject() + ", " + messages[i].getSentDate());
            Object content = messages[i].getContent();
            if ( messages[i].getSubject().contains("ProxyList") && content instanceof MimeMultipart && ((MimeMultipart)content).getCount() > 1 ) {
               BodyPart zip = ((MimeMultipart)content).getBodyPart(1);
               if ( zip.getFileName() != null && zip.getFileName().contains("proxylist") ) {
                  ZipInputStream in = new ZipInputStream(zip.getInputStream());
                  ZipEntry zipEntry = null;
                  while ( (zipEntry = in.getNextEntry()) != null ) {
                     if ( zipEntry.getName().equals("full_list_nopl/_reliable_list.txt") ) {
                        String list = IOUtils.toString(in);
                        list = "lastRefreshTime=" + System.currentTimeMillis() + "\n" + list;
                        FileUtils.writeStringToFile(new File(_proxyListFilename), list);
                        FileUtils.writeStringToFile(new File(LAST_MESSAGE_ID_FILENAME), "" + messageCount);
                        _log.info("updated " + _proxyListFilename + " with list from " + messages[i].getSentDate());
                        return;
                     }
                  }
               }
            }
         }
      }
      finally {
         inbox.close(false);
         store.close();
      }
   }
}
