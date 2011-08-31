package util.spring;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;


public class CoderTest {

   @Test
   public void testCoder() {
      String[] testStrings = { "password", "7Jon5DHqyH3eVAaqV1b0", "a¢H{0òÕ<caÝuâ?º:D¦ç9", "\u0000\uffff" };

      for ( String s : testStrings ) {
         String decoded = Coder.decode(Coder.encode(s));
         assertThat(decoded).isEqualTo(s);
      }
   }
}
