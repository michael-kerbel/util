package util.reflection;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;


public class LambdaMethodNameResolver {

   private static Map<Class, Object> _defaults = new HashMap<>();

   static {
      _defaults.put(String.class, "");
      _defaults.put(Integer.class, 0);
      _defaults.put(Float.class, 0f);
      _defaults.put(Double.class, 0d);
      _defaults.put(Long.class, 0L);
      _defaults.put(Character.class, 'c');
      _defaults.put(Byte.class, (byte)0);
      _defaults.put(int.class, 0);
      _defaults.put(float.class, 0f);
      _defaults.put(double.class, 0d);
      _defaults.put(long.class, 0L);
      _defaults.put(char.class, 'c');
      _defaults.put(byte.class, (byte)0);
   }

   public static <T, U> String getCalledMethodName( Class<T> c, Function<T, U> methodLambda ) {
      Recorder<T> recorder = RecordingObject.create(c);
      methodLambda.apply(recorder.getObject());
      return recorder.getCurrentPropertyName();
   }

   public static <T, U, V> String getCalledMethodName( Class<T> c, BiFunction<T, U, V> methodLambda ) {
      Recorder<T> recorder = RecordingObject.create(c);
      methodLambda.apply(recorder.getObject(), null);
      return recorder.getCurrentPropertyName();
   }


   private static class Recorder<T> {

      private T               t;
      private RecordingObject recorder;


      public Recorder( T t, RecordingObject recorder ) {
         this.t = t;
         this.recorder = recorder;
      }

      public String getCurrentPropertyName() {
         return recorder.getCurrentPropertyName();
      }

      public T getObject() {
         return t;
      }
   }


   private static class RecordingObject implements MethodInterceptor {

      @SuppressWarnings("unchecked")
      public static <T> Recorder<T> create( Class<T> cls ) {
         Enhancer enhancer = new Enhancer();
         enhancer.setSuperclass(cls);
         final RecordingObject recordingObject = new RecordingObject();

         enhancer.setCallback(recordingObject);
         return new Recorder(enhancer.create(), recordingObject);
      }
      private String currentPropertyName = "";

      public String getCurrentPropertyName() {
         return currentPropertyName;
      }

      public Object intercept( Object o, Method method, Object[] os, MethodProxy mp ) throws Throwable {
         if ( method.getName().equals("getCurrentPropertyName") ) {
            return getCurrentPropertyName();
         }
         currentPropertyName = method.getName();
         return _defaults.get(method.getReturnType());
      }
   }

}
