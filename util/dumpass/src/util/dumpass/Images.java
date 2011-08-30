package util.dumpass;

import org.eclipse.swt.graphics.Image;

import util.swt.ResourceManager;


public class Images {

   public static final Image   FIND16           = ResourceManager.getImage(Images.class, "img/find16.png");
   public static final Image   JAR_INTO16       = ResourceManager.getImage(Images.class, "img/jar-into16.png");
   public static final Image   DUMPASS16        = ResourceManager.getImage(Images.class, "img/dump-ass16.png");
   public static final Image   DUMPASS32        = ResourceManager.getImage(Images.class, "img/dump-ass32.png");
   public static final Image   DUMPASS48        = ResourceManager.getImage(Images.class, "img/dump-ass48.png");
   public static final Image   PLAY24           = ResourceManager.getImage(Images.class, "img/play24.png");
   public static final Image   ADD_DIR16        = ResourceManager.getImage(Images.class, "img/add-dir16.png");
   public static final Image   ADD_JAR16        = ResourceManager.getImage(Images.class, "img/add-jar16.png");
   public static final Image   MINUS16          = ResourceManager.getImage(Images.class, "img/minus16.png");
   public static final Image   SEARCH16         = ResourceManager.getImage(Images.class, "img/search16.png");
   public static final Image   SEARCH24         = ResourceManager.getImage(Images.class, "img/search24.png");
   public static final Image   UNDO24           = ResourceManager.getImage(Images.class, "img/undo24.png");
   public static final Image   CONFIRM_DELETE16 = ResourceManager.getImage(Images.class, "img/confirm-delete16.png");
   public static final Image   EDIT16           = ResourceManager.getImage(Images.class, "img/edit16.png");
   public static final Image   CLEANUP16        = ResourceManager.getImage(Images.class, "img/cleanup16.png");
   public static final Image   REPAIR24         = ResourceManager.getImage(Images.class, "img/repair24.png");

   public static final Image[] SHELL_IMAGES     = new Image[] { Images.DUMPASS48, Images.DUMPASS32, Images.DUMPASS16 };

}
