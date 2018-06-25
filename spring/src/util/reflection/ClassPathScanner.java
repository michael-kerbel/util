package util.reflection;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;


public class ClassPathScanner {

   /**
    * @return all non-abstract classes of the given type in the base package available in the current classpath
    * */
   public static Set<Class> findAllClassesOfType( String basePackage, Class<?> type ) {
      Set<Class> classes = new HashSet<>();

      ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AssignableTypeFilter(type));
      Set<BeanDefinition> components = scanner.findCandidateComponents(basePackage);
      for ( BeanDefinition bean : components ) {
         if ( !bean.isAbstract() ) {
            try {
               classes.add(Class.forName(bean.getBeanClassName()));
            }
            catch ( ClassNotFoundException e ) {
               throw new RuntimeException("Failed to convert component " + bean.getBeanClassName() + " to class", e);
            }
         }
      }

      return classes;
   }
}
