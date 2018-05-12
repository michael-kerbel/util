package util.reflection;

import org.junit.Assert;
import org.junit.Test;


public class LambdaMethodNameResolverTest {


   @Test
   public void testGetter(){
      String calledMethodName = LambdaMethodNameResolver.getCalledMethodName(CalledClass.class, CalledClass::getter);
      Assert.assertEquals("getter", calledMethodName);
   }
   @Test
   public void testGetterWithParams(){
      String calledMethodName = LambdaMethodNameResolver.getCalledMethodName(CalledClass.class, CalledClass::getterWithParam);
      Assert.assertEquals("getterWithParam", calledMethodName);
   }


   public static class CalledClass{

      public long getter(){
         return 1L;
      }

      public long getterWithParam(String s){
         return 1L;
      }

   }
}