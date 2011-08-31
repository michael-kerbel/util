package util.dump;

import org.junit.Test;

import util.reflection.FieldAccessor;


public class InfiniteGroupIndexTest extends AbstractGroupIndexTest {

   @Test
   public void testExternalizableKeyIndex() throws Exception {
      testIndex("_groupExternalizable", new InfiniteGroupIndexConfig() {

         @Override
         public Object createKey( int id ) {
            return new ExternalizableId(id);
         }
      });
   }

   @Test
   public void testIntKeyIndex() throws Exception {
      testIndex("_groupInt", new InfiniteGroupIndexConfig() {

         @Override
         public Object createKey( int id ) {
            return Integer.valueOf(id);
         }
      });
   }

   @Test
   public void testLongKeyIndex() throws Exception {
      testIndex("_groupLong", new InfiniteGroupIndexConfig() {

         @Override
         public Object createKey( int id ) {
            return Long.valueOf(id);
         }
      });
   }

   @Test
   public void testStringKeyIndex() throws Exception {
      testIndex("_groupString", new InfiniteGroupIndexConfig() {

         @Override
         public Object createKey( int id ) {
            return (id < 0 ? "" : "+") + id;
         }
      });
   }

   public abstract static class InfiniteGroupIndexConfig extends TestConfiguration {

      @Override
      public NonUniqueIndex createIndex( Dump dump, FieldAccessor fieldAccessor ) {
         return new InfiniteGroupIndex<Bean>(dump, fieldAccessor, 25000);
      }
   }


}
