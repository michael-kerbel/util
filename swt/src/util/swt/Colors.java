package util.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;


public class Colors {

   public static final Color  BLACK             = ResourceManager.getColor(SWT.COLOR_BLACK);
   public static final Color  WHITE             = ResourceManager.getColor(SWT.COLOR_WHITE);
   public static final Color  GRAY              = ResourceManager.getColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
   public static final Color  RED               = ResourceManager.getColor(SWT.COLOR_RED);
   public static final Color  BLUE              = ResourceManager.getColor(SWT.COLOR_BLUE);
   public static final Color  GREEN             = ResourceManager.getColor(SWT.COLOR_GREEN);
   public static final Color  YELLOW            = ResourceManager.getColor(SWT.COLOR_YELLOW);
   public static final Color  DARK_RED          = ResourceManager.getColor(SWT.COLOR_DARK_RED);
   public static final Color  DARK_BLUE         = ResourceManager.getColor(SWT.COLOR_DARK_BLUE);
   public static final Color  DARK_YELLOW       = ResourceManager.getColor(SWT.COLOR_DARK_YELLOW);
   public static final Color  DARK_GRAY         = ResourceManager.getColor(SWT.COLOR_DARK_GRAY);
   /* this is some weird shit: When using DARK_GREEN_SYSTEM as background color in Tables on Windows, it stays black. 
    * Workaround is to change the color */
   private static final Color DARK_GREEN_SYSTEM = ResourceManager.getColor(SWT.COLOR_DARK_GREEN);
   public static final Color  DARK_GREEN        = ResourceManager.getColor(DARK_GREEN_SYSTEM.getRed(), DARK_GREEN_SYSTEM.getGreen() + 1, DARK_GREEN_SYSTEM
                                                      .getBlue());

   public static final Color  SELECTION_YELLOW  = ResourceManager.getColor(0xff, 0xff, 0xa0);
   public static final Color  SELECTION_GREEN   = ResourceManager.getColor(0xc8, 0xff, 0x80);
   public static final Color  SELECTION_RED     = ResourceManager.getColor(0xff, 0xc0, 0xb0);

   public static final Color  LIST_BACKGROUND   = ResourceManager.getColor(SWT.COLOR_LIST_BACKGROUND);
   public static final Color  WIDGET_BACKGROUND = ResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND);

}
