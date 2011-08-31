package util.dump;

import org.junit.Test;

import util.reflection.FieldAccessor;


public class GroupIndexTest extends AbstractGroupIndexTest {

   @Test
   public void testExternalizableKeyIndex() throws Exception {
      testIndex("_groupExternalizable", new GroupIndexTestConfig() {

         @Override
         public Object createKey( int id ) {
            return new ExternalizableId(id);
         }
      });
   }

   @Test
   public void testIntKeyIndex() throws Exception {
      testIndex("_groupInt", new GroupIndexTestConfig() {

         @Override
         public Object createKey( int id ) {
            return Integer.valueOf(id);
         }
      });
   }

   @Test
   public void testLongKeyIndex() throws Exception {
      testIndex("_groupLong", new GroupIndexTestConfig() {

         @Override
         public Object createKey( int id ) {
            return Long.valueOf(id);
         }
      });
   }

   @Test
   public void testStringKeyIndex() throws Exception {
      testIndex("_groupString", new GroupIndexTestConfig() {

         @Override
         public Object createKey( int id ) {
            return (id < 0 ? "" : "+") + id;
         }
      });
   }

   public abstract static class GroupIndexTestConfig extends TestConfiguration {

      @Override
      public NonUniqueIndex createIndex( Dump dump, FieldAccessor fieldAccessor ) {
         return new GroupIndex<Bean>(dump, fieldAccessor);
      }
   }
}
