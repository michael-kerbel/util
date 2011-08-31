package util.spring;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionVisitor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.StringValueResolver;


public class PasswordDecodingPostProcessor implements BeanFactoryPostProcessor, BeanNameAware, BeanFactoryAware {

   private static final Pattern PASSWORD_PATTERN = Pattern.compile("!\\{(.+)\\}");

   private String               _beanName;
   private BeanFactory          _beanFactory;


   public void postProcessBeanFactory( ConfigurableListableBeanFactory beanFactory ) throws BeansException {
      StringValueResolver valueResolver = new PasswordResolvingStringValueResolver();
      BeanDefinitionVisitor visitor = new BeanDefinitionVisitor(valueResolver);

      String[] beanNames = beanFactory.getBeanDefinitionNames();
      for ( int i = 0; i < beanNames.length; i++ ) {
         // Check that we're not parsing our own bean definition,
         // to avoid failing on unresolvable placeholders in properties file locations.
         if ( !(beanNames[i].equals(_beanName) && beanFactory.equals(_beanFactory)) ) {
            BeanDefinition bd = beanFactory.getBeanDefinition(beanNames[i]);
            try {
               visitor.visitBeanDefinition(bd);
            }
            catch ( BeanDefinitionStoreException ex ) {
               throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanNames[i], ex.getMessage());
            }
         }
      }

   }

   public void setBeanFactory( BeanFactory beanFactory ) throws BeansException {
      _beanFactory = beanFactory;
   }

   public void setBeanName( String name ) {
      _beanName = name;
   }


   private class PasswordResolvingStringValueResolver implements StringValueResolver {

      public String resolveStringValue( String strVal ) throws BeansException {
         Matcher matcher = PASSWORD_PATTERN.matcher(strVal);
         if ( matcher.matches() ) return Coder.decode(matcher.group(1));
         return strVal;
      }
   }
}
