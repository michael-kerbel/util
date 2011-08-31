package util.spring;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class PasswordDecodingPostProcessorTest {

   @Test
   public void testPostProcessor() throws Exception {
      ApplicationContext context = new ClassPathXmlApplicationContext("spring-beans.xml", getClass());
      TestBean t = (TestBean)context.getBean("testBean");

      assertThat(t.getPassword()).isEqualTo("password");
   }

}
