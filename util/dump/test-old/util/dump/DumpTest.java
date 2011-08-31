package util.dump;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


public class DumpTest {


   @Before
   @After
   public void deleteOldTestDumps() {
      File[] dumpFile = new File(".").listFiles(new FileFilter() {

         public boolean accept( File f ) {
            return f.getName().startsWith("DumpTest.");
         }
      });
      for ( File df : dumpFile ) {
         if ( !df.delete() ) System.out.println("Failed to delete old dump file " + df);
      }
   }

   @Test
   public void testIterateEmptyDump() throws IOException, NoSuchFieldException {
      File dumpFile = new File("DumpTest.dmp");
      Dump<Bean> dump = new Dump<Bean>(Bean.class, dumpFile);

      for ( Bean bean : dump ) {
         Assert.assertTrue("Element returned during iteration of empty dump", bean != bean);
      }

      dump.close();
   }

   @Test
   public void testReOpening() throws IOException, NoSuchFieldException {
      File dumpFile = new File("DumpTest.dmp");
      Dump<Bean> dump = new Dump<Bean>(Bean.class, dumpFile);
      UniqueIndex<Bean> index = new UniqueIndex<Bean>(dump, "_id");
      for ( int j = 0; j < 100000; j++ ) {
         dump.add(new Bean(j));
      }
      Bean bean = index.lookup(50000);
      Assert.assertNotNull("Bean added after reopening not found using index", bean);
      Assert.assertEquals("wrong index for bean retrieved using index", 50000, bean._id);

      int i = 0;
      for ( Bean b : dump ) {
         Assert.assertEquals("Wrong index during iteration", i++, b._id);
      }
      Assert.assertEquals("Iterated wrong number of elements", 100000, i);

      dump.close();

      dump = new Dump<Bean>(Bean.class, dumpFile);
      index = new UniqueIndex<Bean>(dump, "_id");
      for ( int j = 100000; j < 200000; j++ ) {
         dump.add(new Bean(j));
      }

      bean = index.lookup(150000);
      Assert.assertNotNull("Bean added after reopening not found using index", bean);
      Assert.assertEquals("wrong index for bean retrieved using index", 150000, bean._id);

      i = 0;
      for ( Bean b : dump ) {
         if ( i % 2 == 0 ) dump.deleteLast();
         Assert.assertEquals("Wrong index during iteration", i++, b._id);
      }
      Assert.assertEquals("Iterated wrong number of elements", 200000, i);

      /* test deletions */
      i = 0;
      for ( Bean b : dump ) {
         Assert.assertEquals("Wrong index during iteration", i * 2 + 1, b._id);
         i++;
      }
      Assert.assertEquals("Iterated wrong number of elements", 100000, i);

      dump.close();

      dump = new Dump<Bean>(Bean.class, dumpFile);

      /* test deletions after re-opening*/
      i = 0;
      for ( Bean b : dump ) {
         Assert.assertEquals("Wrong index during iteration", i * 2 + 1, b._id);
         i++;
      }
      Assert.assertEquals("Iterated wrong number of elements", 100000, i);

      dump.close();
   }

   public static class Bean extends Externalizer {

      @externalize(1)
      private int _id;

      public Bean() {}

      public Bean( int id ) {
         _id = id;
      }
   }
}
