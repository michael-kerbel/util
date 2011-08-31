package util.swt.viewerbuilder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.eclipse.swt.SWT;


@Retention(RetentionPolicy.RUNTIME)
@Target( { ElementType.METHOD })
public @interface Column {

   String caption();

   int index();

   boolean defaultColumn() default true;

   int style() default SWT.LEFT;

   boolean useInFilter() default true;

   int width() default 50;
}
