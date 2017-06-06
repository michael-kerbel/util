package util.spring;

import java.beans.PropertyEditorSupport;


/**
 * Use this class with such a config 
 * <pre>
 *   &lt;bean class="org.springframework.beans.factory.config.CustomEditorConfigurer"&gt;
 *     &lt;property name="customEditors"&gt;
 *       &lt;map&gt;
 *         &lt;entry key="java.lang.Enum"&gt;
 *           &lt;bean class="util.spring.EnumPropertyEditor"/&gt;
 *         &lt;/entry&gt;
 *       &lt;/map&gt;
 *     &lt;/property&gt;
 *   &lt;/bean&gt;
 * </pre>
 * in order to suppress this Spring warning:
 * <pre>  
 * PropertyEditor [com.sun.beans.editors.EnumEditor] found through deprecated global PropertyEditorManager fallback - consider using a more isolated form of registration, e.g. on the BeanWrapper/BeanFactory!
 * </pre>  
 */
public final class EnumPropertyEditor extends PropertyEditorSupport {

   public EnumPropertyEditor() {}

   @Override
   public String getAsText() {
      return (String)getValue();
   }

   @Override
   public void setAsText( String text ) throws IllegalArgumentException {
      setValue(text);
   }
}