package util.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;


public class BorderPainter implements PaintListener {

   public static final String KEY_DRAW_BORDER = "FormWidgetFactory.drawBorder"; //$NON-NLS-1$
   public static final String TREE_BORDER     = "treeBorder";                  //$NON-NLS-1$
   public static final String TEXT_BORDER     = "textBorder";                  //$NON-NLS-1$

   public static final Color BORDER_COLOR = ResourceManager.getColor(blend( //
         ResourceManager.getColor(SWT.COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT).getRGB(), //
         ResourceManager.getColor(SWT.COLOR_BLACK).getRGB() //
         , 80));

   private static final BorderPainter INSTANCE = new BorderPainter();

   public static void paintBordersFor( Composite parent ) {
      parent.addPaintListener(INSTANCE);
   }

   private static int blend( int v1, int v2, int ratio ) {
      int b = (ratio * v1 + (100 - ratio) * v2) / 100;
      return Math.min(255, b);
   }

   private static RGB blend( RGB c1, RGB c2, int ratio ) {
      int r = blend(c1.red, c2.red, ratio);
      int g = blend(c1.green, c2.green, ratio);
      int b = blend(c1.blue, c2.blue, ratio);
      return new RGB(r, g, b);
   }

   private BorderPainter() {}

   @Override
   public void paintControl( PaintEvent event ) {
      Composite composite = (Composite)event.widget;
      Control[] children = composite.getChildren();
      for ( int i = 0; i < children.length; i++ ) {
         Control c = children[i];
         boolean inactiveBorder = false;
         boolean textBorder = false;
         if ( !c.isVisible() ) {
            continue;
         }
         if ( c.getEnabled() == false && !(c instanceof CCombo) ) {
            continue;
         }

         Object flag = c.getData(KEY_DRAW_BORDER);
         if ( flag != null ) {
            if ( flag.equals(Boolean.FALSE) ) {
               continue;
            }
            if ( flag.equals(TREE_BORDER) ) {
               inactiveBorder = true;
            } else if ( flag.equals(TEXT_BORDER) ) {
               textBorder = true;
            }
         }
         if ( !inactiveBorder && (c instanceof Text || c instanceof CCombo || textBorder) ) {
            Rectangle b = c.getBounds();
            GC gc = event.gc;
            gc.setForeground(c.getBackground());
            gc.drawRectangle(b.x - 1, b.y - 1, b.width + 1, b.height + 1);
            gc.setForeground(BORDER_COLOR);
            if ( c instanceof CCombo ) {
               gc.drawRectangle(b.x - 1, b.y - 1, b.width + 1, b.height + 1);
            } else {
               gc.drawRectangle(b.x - 1, b.y - 2, b.width + 1, b.height + 3);
            }
         } else if ( inactiveBorder || c instanceof Table || c instanceof Tree ) {
            Rectangle b = c.getBounds();
            GC gc = event.gc;
            gc.setForeground(BORDER_COLOR);
            gc.drawRectangle(b.x - 1, b.y - 1, b.width + 1, b.height + 1);
         }
      }
   }
}
