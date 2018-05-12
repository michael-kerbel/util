package util.reflection;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;


public class ReflectionTest {

   @Test
   public void testGetMethodCalledByLambda_Getter() {
      Method calledMethod = Reflection.getMethodCalledByLambda(CalledClass.class, CalledClass::getter);
      Assert.assertNotNull(calledMethod);
      Assert.assertEquals("getter", calledMethod.getName());
   }

   @Test
   public void testGetMethodCalledByLambda_GetterWithParams() {
      Method calledMethod = Reflection.getMethodCalledByLambda(CalledClass.class, CalledClass::getterWithParam);
      Assert.assertNotNull(calledMethod);
      Assert.assertEquals("getterWithParam", calledMethod.getName());
   }


   public static class CalledClass {

      public long getter() {
         return 1L;
      }

      public long getterWithParam( String s ) {
         return 1L;
      }

   }
}