package util.string;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;


public class StringFilterTest {

   private static final String LOREM_IPSUM = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr.";

   @Test
   public void testAnd() {
      matches("lore dol", LOREM_IPSUM);
      matches("lorem dolor", LOREM_IPSUM);
      doesNotMatch("lorem aaaaa", LOREM_IPSUM);
   }

   @Test
   public void testComplex() {
      matches("aaa|dolor -aaa \"sit\"", LOREM_IPSUM);
      matches("dolor \"sit\"", LOREM_IPSUM);
      matches("\"dolor sit\" amet \"consetetur sadipscing\" -\"aaaa\"", LOREM_IPSUM);
      matches("\"dolor sit\" amet \"consetetur sadipscing\" \"aaaa\"|elitr|\"bbbb\"", LOREM_IPSUM);
   }

   @Test
   public void testNot() {
      matches("-aaaaa", LOREM_IPSUM);
      doesNotMatch("-\"Lorem ipsum\" -aaaa", LOREM_IPSUM);
   }

   @Test
   public void testOr() {
      matches("aaa|dolor", LOREM_IPSUM);
      matches("aaa|dolor sit|aaa", LOREM_IPSUM);
      doesNotMatch("aaa|bbb sit|lorem", LOREM_IPSUM);
   }

   @Test
   public void testOrPhrase() {
      matches("\"aaa\"|\"sit\"", LOREM_IPSUM);
      matches("\"aaa\"|\"dolor sit\"", LOREM_IPSUM);
      matches("\"aaa\"|\"dolor sit\"|bbb", LOREM_IPSUM);
      matches("\"aaa\"|\"dolor sit\"|\"bbb\"", LOREM_IPSUM);
      doesNotMatch("\"aaa\"|\"dolo\"", LOREM_IPSUM);
      doesNotMatch("\"aaa\"|\"dolor si\"", LOREM_IPSUM);
   }

   @Test
   public void testPhrase() {
      matches("\"ipsum\"", LOREM_IPSUM);
      matches("\"lorem ipsum\"", LOREM_IPSUM);
      doesNotMatch("\"lore\"", LOREM_IPSUM);
   }

   @Test
   public void testSimple() {
      matches("lorem", LOREM_IPSUM);
      doesNotMatch("aaaaa", LOREM_IPSUM);
   }

   @Test
   public void testSubstring() {
      matches("ips", LOREM_IPSUM);
      matches("psum", LOREM_IPSUM);
   }

   private void doesNotMatch( String filterString, String text ) {
      assertThat(new StringFilter(filterString).filter(text)).as("Filter '" + filterString + "' did match on '" + text + "'").isTrue();
   }

   private void matches( String filterString, String text ) {
      assertThat(new StringFilter(filterString).filter(text)).as("Filter '" + filterString + "' did not match on '" + text + "'").isFalse();
   }
}