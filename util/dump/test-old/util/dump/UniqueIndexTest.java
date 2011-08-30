package util.dump;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import util.reflection.FieldFieldAccessor;
import util.reflection.Reflection;


public class UniqueIndexTest {

   private static final String DUMP_FILENAME = "DumpTest.dmp";
   private static final int    DUMP_SIZE     = 100000;
   private static final int    READ_NUMBER   = 1000;
   private static final int    BEAN_SIZE     = 10;

   private Random              _random;

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

   @Before
   public void initRandom() {
      long seed = System.currentTimeMillis();
      _random = new Random(seed);
      System.out.println("Seed used for this DumpTest run: " + seed);
   }

   @After
   public void printMemory() {
      System.gc();
      long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      System.out.println(mem / (1024 * 1024) + " MB used after test run");
   }

   @Test
   public void testExternalizableKeyIndex() throws Exception {
      testIndex("_idExternalizable", new TestConfiguration() {

         @Override
         public Object createKey( int id ) {
            return new ExternalizableId(id);
         }
      });

   }

   @Test
   public void testIntKeyIndex() throws Exception {
      testIndex("_idInt", new TestConfiguration() {

         @Override
         public Object createKey( int id ) {
            return id;
         }
      });
   }

   @Test
   public void testLongKeyIndex() throws Exception {
      testIndex("_idLong", new TestConfiguration() {

         @Override
         public Object createKey( int id ) {
            return (long)id;
         }
      });
   }

   @Test
   public void testLongObjectKeyIndex() throws Exception {
      testIndex("_idLongObject", new TestConfiguration() {

         @Override
         public Object createKey( int id ) {
            return (long)id;
         }
      });
   }

   @Test
   public void testRecreateIndex() throws NoSuchFieldException, IOException {
      File dumpFile = new File(DUMP_FILENAME);

      Dump<Bean> dump = new Dump<Bean>(Bean.class, dumpFile);
      Field field = Reflection.getField(Bean.class, "_idInt");
      UniqueIndex<Bean> index = new UniqueIndex<Bean>(dump, new FieldFieldAccessor(field));

      StringBuilder sb = new StringBuilder("-");
      for ( int i = 0; i < BEAN_SIZE - 15; i++ ) { // 15 is an estimation for the size of the Bean instance without this padding
         sb.append('0');
      }

      long t = System.currentTimeMillis();
      for ( int i = 0; i < DUMP_SIZE; i++ ) {
         dump.add(new Bean(i, i + sb.toString()));
      }
      System.out.println("Written " + DUMP_SIZE + " instances to dump. Needed " + (System.currentTimeMillis() - t) / (float)DUMP_SIZE + " ms/instance.");

      dump.close();

      System.out.println("Closing and re-opening dump, deleting index");
      Assert.assertTrue("Failed to delete index", new File(DUMP_FILENAME + "._idInt.lookup").delete() && !new File(DUMP_FILENAME + "._idInt.lookup").exists());

      dump = new Dump<Bean>(Bean.class, dumpFile);
      index = new UniqueIndex<Bean>(dump, new FieldFieldAccessor(field));

      t = System.currentTimeMillis();
      for ( int j = 0; j < READ_NUMBER; j++ ) {
         int i = _random.nextInt(DUMP_SIZE);
         Bean bean = index.lookup(i);
         Assert.assertNotNull("no Bean for index " + i, bean);
         Assert.assertEquals(i, bean._idInt);
         Assert.assertTrue(bean._data.startsWith(i + "-"));
      }
      System.out.println("Read " + READ_NUMBER + " instances from dump. Needed " + (System.currentTimeMillis() - t) / (float)READ_NUMBER + " ms/instance.");

      Bean nonExistingBean = index.lookup(DUMP_SIZE + 1);
      Assert.assertNull(nonExistingBean);

      dump.close();
   }

   @Test
   public void testStringKeyIndex() throws Exception {
      testIndex("_idString", new TestConfiguration() {

         @Override
         public Object createKey( int id ) {
            return (id < 0 ? "" : "+") + id;
         }
      });
   }

   protected void testIndex( String fieldName, TestConfiguration config ) throws Exception {
      File dumpFile = new File(DUMP_FILENAME);

      /* create dump and index */
      Dump<Bean> dump = new Dump<Bean>(Bean.class, dumpFile);
      Field field = Reflection.getField(Bean.class, fieldName);
      UniqueIndex<Bean> index = new UniqueIndex<Bean>(dump, new FieldFieldAccessor(field));

      StringBuilder sb = new StringBuilder("-");
      for ( int i = 0; i < BEAN_SIZE - 15; i++ ) { // 15 is an estimation for the size of the Bean instance without this padding
         sb.append('0');
      }

      /* add some elements */
      long t = System.currentTimeMillis();
      for ( int i = 0; i < DUMP_SIZE; i++ ) {
         dump.add(new Bean(i, i + sb.toString()));
      }
      System.out.println("Written " + DUMP_SIZE + " instances to dump. Needed " + (System.currentTimeMillis() - t) / (float)DUMP_SIZE + " ms/instance.");


      testLookup(config, field, index);


      dump.close();

      System.out.println("Closing and re-opening dump");

      dump = new Dump<Bean>(Bean.class, dumpFile);
      index = new UniqueIndex<Bean>(dump, new FieldFieldAccessor(field));


      testLookup(config, field, index);


      /* test lookup of non-existing key */
      Object k = config.createKey(DUMP_SIZE + 1);
      Bean nonExistingBean = index.lookup(k);
      Assert.assertNull(nonExistingBean);


      /* iterate dump and delete half of it */
      t = System.currentTimeMillis();
      int id = 0;
      int deletions = 0;
      for ( Bean bean : dump ) {
         Assert.assertEquals(config.createKey(id), field.get(bean));
         Assert.assertTrue("unexpected bean data", bean._data.startsWith("" + id));
         if ( id % 2 == 0 ) {
            Bean deleted = dump.deleteLast();
            Assert.assertEquals("deleted bean != iterated bean", deleted, bean);
            deletions++;
         }
         id++;
      }
      System.out.println("Iterated the whole dump. Deleted " + deletions + " items. Needed " + (System.currentTimeMillis() - t) + " ms.");


      /* lookup and assert deletions */
      t = System.currentTimeMillis();
      for ( int j = 0; j < READ_NUMBER; j++ ) {
         id = _random.nextInt(DUMP_SIZE);
         k = config.createKey(id);
         Bean bean = index.lookup(k);
         if ( id % 2 == 0 ) {
            Assert.assertNull("deleted Bean with index " + k + " is still accessable", bean);
         } else {
            Assert.assertNotNull("no Bean for index " + k, bean);
            Assert.assertEquals(k, field.get(bean));
            Assert.assertTrue(bean._data.startsWith(id + "-"));
         }
      }
      System.out.println("Read " + READ_NUMBER + " instances from dump. Needed " + (System.currentTimeMillis() - t) / (float)READ_NUMBER + " ms/instance.");


      /* iterate dump and update beans */
      t = System.currentTimeMillis();
      id = 1;
      int updates = 0;
      for ( Bean bean : dump ) {
         Assert.assertEquals(config.createKey(id), field.get(bean));
         Assert.assertTrue("unexpected bean data", bean._data.startsWith("" + id));
         if ( id % 3 == 0 ) {
            /* update without changing externalization size of bean */
            long oldDumpSize = dump._outputStream._n;
            Bean updatedBean = new Bean(-bean._idInt, bean._data);
            Bean oldVersion = dump.updateLast(updatedBean);
            Assert.assertEquals("old bean != iterated bean", oldVersion, bean);
            Assert.assertEquals("dump has grown, even though the update was overwrite compatible", oldDumpSize, dump._outputStream._n);
            updates++;
         } else {
            /* update and change externalization size of bean */
            Bean updatedBean = new Bean(bean._idInt, bean._data.replaceFirst("-", "++"));
            Bean oldVersion = dump.updateLast(updatedBean);
            Assert.assertEquals("old bean != iterated bean", oldVersion, bean);
            updates++;
         }
         id += 2;
      }
      System.out.println("Iterated the whole dump. Updated " + updates + " items. Needed " + (System.currentTimeMillis() - t) + " ms.");


      testLookupAfterUpdates(config, field, index);


      dump.close();

      System.out.println("Closing and re-opening dump");

      dump = new Dump<Bean>(Bean.class, dumpFile);
      index = new UniqueIndex<Bean>(dump, new FieldFieldAccessor(field));


      testLookupAfterUpdates(config, field, index);


      dump.close();


      /* delete index meta file to invalidate the index */
      File[] metaFiles = new File(".").listFiles(new FileFilter() {

         public boolean accept( File f ) {
            return f.getName().startsWith("DumpTest.") && f.getName().endsWith("meta");
         }
      });
      for ( File df : metaFiles ) {
         Assert.assertTrue("Failed to delete meta file " + df, df.delete());
      }
      /* re-open, enforcing the index to be re-created */
      dump = new Dump<Bean>(Bean.class, dumpFile);
      index = new UniqueIndex<Bean>(dump, new FieldFieldAccessor(field));


      /* after having re-created the index, repeat last test */
      testLookupAfterUpdates(config, field, index);


      dump.close();
   }

   private void testLookup( TestConfiguration config, Field field, UniqueIndex<Bean> index ) throws IllegalAccessException {
      long t;
      t = System.currentTimeMillis();
      for ( int j = 0; j < READ_NUMBER; j++ ) {
         int id = _random.nextInt(DUMP_SIZE);
         Object k = config.createKey(id);
         Bean bean = index.lookup(k);
         Assert.assertNotNull("no Bean for index " + k, bean);
         Assert.assertEquals(k, field.get(bean));
         Assert.assertTrue(bean._data.startsWith(id + "-"));
      }
      System.out.println("Read " + READ_NUMBER + " instances from dump. Needed " + (System.currentTimeMillis() - t) / (float)READ_NUMBER + " ms/instance.");
   }

   private void testLookupAfterUpdates( TestConfiguration config, Field field, UniqueIndex<Bean> index ) throws IllegalAccessException {
      long t;
      Object k;
      int id;
      t = System.currentTimeMillis();
      for ( int j = 0; j < READ_NUMBER; j++ ) {
         id = _random.nextInt(DUMP_SIZE);
         if ( id % 3 == 0 ) id = -id;
         k = config.createKey(id);
         Bean bean = index.lookup(k);
         if ( id % 2 == 0 ) {
            Assert.assertNull("deleted Bean with index " + k + " is still accessable", bean);
         } else if ( Math.abs(id) % 3 == 0 ) {
            Assert.assertNotNull("no Bean for index " + k, bean);
            Assert.assertEquals(config.createKey(id), field.get(bean));
            Assert.assertTrue("bean data wrong: id=" + id + ", data=" + bean._data, bean._data.startsWith((-id) + "-"));
         } else {
            Assert.assertNotNull("no Bean for index " + k, bean);
            Assert.assertEquals(k, field.get(bean));
            Assert.assertTrue(bean._data.startsWith(id + "++"));
         }
      }
      System.out.println("Read " + READ_NUMBER + " instances from dump. Needed " + (System.currentTimeMillis() - t) / (float)READ_NUMBER + " ms/instance.");
   }

   public static class Bean extends Externalizer {

      @externalize(1)
      long             _idLong;
      @externalize(2)
      int              _idInt;
      @externalize(3)
      String           _idString;
      @externalize(4)
      Long             _idLongObject;
      @externalize(5)
      ExternalizableId _idExternalizable;
      @externalize(10)
      String           _data;

      public Bean() {
      // for Externalization
      }

      public Bean( int id, String data ) {
         _idLong = id;
         _idInt = id;
         _idString = (id < 0 ? "" : "+") + id;
         _idLongObject = (long)id;
         _idExternalizable = new ExternalizableId(id);
         _data = data;
      }

      @Override
      public boolean equals( Object obj ) {
         if ( this == obj ) return true;
         if ( obj == null ) return false;
         if ( getClass() != obj.getClass() ) return false;
         Bean other = (Bean)obj;
         if ( _data == null ) {
            if ( other._data != null ) return false;
         } else if ( !_data.equals(other._data) ) return false;
         if ( _idExternalizable == null ) {
            if ( other._idExternalizable != null ) return false;
         } else if ( !_idExternalizable.equals(other._idExternalizable) ) return false;
         if ( _idInt != other._idInt ) return false;
         if ( _idLong != other._idLong ) return false;
         if ( _idLongObject == null ) {
            if ( other._idLongObject != null ) return false;
         } else if ( !_idLongObject.equals(other._idLongObject) ) return false;
         if ( _idString == null ) {
            if ( other._idString != null ) return false;
         } else if ( !_idString.equals(other._idString) ) return false;
         return true;
      }

      @Override
      public int hashCode() {
         throw new UnsupportedOperationException("hashCode() not needed.");
      }
   }

   public static class ExternalizableId extends Externalizer {

      @externalize(1)
      long _id;

      public ExternalizableId() {
      // for Externalization
      }

      public ExternalizableId( long id ) {
         _id = id;
      }

      @Override
      public boolean equals( Object obj ) {
         if ( this == obj ) return true;
         if ( obj == null ) return false;
         if ( getClass() != obj.getClass() ) return false;
         ExternalizableId other = (ExternalizableId)obj;
         if ( _id != other._id ) return false;
         return true;
      }

      @Override
      public int hashCode() {
         final int prime = 31;
         int result = 1;
         result = prime * result + (int)(_id ^ (_id >>> 32));
         return result;
      }
   }

   protected static abstract class TestConfiguration {

      public abstract Object createKey( int id );
   }

}
