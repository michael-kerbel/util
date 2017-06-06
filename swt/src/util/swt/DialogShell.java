package util.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;


public abstract class DialogShell {

   public static final Image IMAGE_OK            = ResourceManager.getImage(DialogShell.class, "ok.png");
   public static final Image IMAGE_CANCEL        = ResourceManager.getImage(DialogShell.class, "cancel.png");
   public static final Image IMAGE_OK_HOT        = ResourceManager.getImage(DialogShell.class, "ok-hot.png");
   public static final Image IMAGE_CANCEL_HOT    = ResourceManager.getImage(DialogShell.class, "cancel-hot.png");
   public static Color[]     BG_COLORS           = new Color[] { ResourceManager.getColor(224, 158, 33), ResourceManager.getColor(248, 153, 61),
         ResourceManager.getColor(247, 137, 41), ResourceManager.getColor(246, 130, 31), ResourceManager.getColor(246, 130, 31),
         ResourceManager.getColor(246, 130, 31), ResourceManager.getColor(247, 137, 41), ResourceManager.getColor(248, 153, 61),
         ResourceManager.getColor(249, 176, 92) };
   public static Image       BG_IMAGE            = ResourceManager.getImage(DialogShell.class, "dialog-shell-bg.png");

   private static int        RESIZE_BORDER_X     = 2;                                                                 // 32 = OS.SM_CXSIZEFRAME
   private static int        RESIZE_BORDER_Y     = 2;                                                                 // 33 = OS.SM_CYSIZEFRAME
   private static final int  HEADER_FOOTER_ALPHA = 220;

   static {
      Integer i = (Integer)Win32OS.call("GetSystemMetrics", new Class[] { int.class }, 32);
      if ( i != null ) {
         RESIZE_BORDER_X = i;
      }
      i = (Integer)Win32OS.call("GetSystemMetrics", new Class[] { int.class }, 33);
      if ( i != null ) {
         RESIZE_BORDER_Y = i;
      }
   }


   protected Shell           _shell;
   protected ToolItem        _ok;
   protected ToolItem        _cancel;
   protected Image           _backgroundImageTransparentShell;
   protected Image           _backgroundImageShell;
   protected int             _style;
   private Shell             _transparentShell;
   private BackgroundPainter _transparentShellBackgroundPainter;

   public DialogShell() {
      this(SWT.CANCEL | SWT.OK);
   }

   public DialogShell( int style ) {
      _style = style;
   }

   public abstract void cancel();

   public abstract void createContent( Composite parent );

   public abstract String getDescriptionText();

   public int getHeight() {
      return SWT.DEFAULT;
   }

   public abstract Image[] getImages();

   public int getStyle() {
      return _style;
   }

   public abstract String getTitle();

   public abstract int getWidth();

   public abstract void ok();

   /**
    * If you have a long running task in okPreDispose, but you want to close the Shell immediately, put the task into okPostDispose()
    */
   public void okPostDispose() {}

   public void open( boolean blockOnOpen ) {
      if ( Display.getDefault().getThread().equals(Thread.currentThread()) ) {
         init();
      } else {
         Display.getDefault().asyncExec(new Runnable() {

            public void run() {
               init();
            }
         });
      }
      if ( blockOnOpen ) {
         runEventLoop();
      }
   }

   protected void createTitle( Composite parent ) {
      final Label title = new Label(parent, SWT.WRAP | SWT.LEFT);
      title.setText(getTitle());
      title.setFont(ResourceManager.getBoldFont(title.getFont()));
      title.addPaintListener(new PaintListener() {

         public void paintControl( PaintEvent e ) {
            Point size = ((Control)e.widget).getSize();

            Color fg = BG_COLORS[1];

            Image image = new Image(e.widget.getDisplay(), size.x, size.y);
            GC gc = new GC(image);
            gc.setForeground(fg);
            gc.setFont(title.getFont());
            int extendX = gc.stringExtent(getTitle()).x;
            if ( extendX >= size.x ) {
               gc.dispose();
               image.dispose();
               return;
            }
            gc.drawText(getTitle(), 1, 0, true);
            gc.dispose();
            ImageData blurred = SWTUtils.blur(image.getImageData(), 1);
            blurred.transparentPixel = blurred.getPixel(size.x - 1, 0); // magic

            // darken it
            for ( int x = 0; x <= Math.min(extendX, size.x - 1); x++ ) {
               for ( int y = 0, length = size.y; y < length; y++ ) {
                  int index = (y * blurred.bytesPerLine) + (x * 3);
                  int b = (blurred.data[index] & 0xFF);
                  int g = (blurred.data[index + 1] & 0xFF);
                  int r = (blurred.data[index + 2] & 0xFF);
                  if ( r + g + b < 0xf8 + 0xf8 + 0xf8 ) {
                     float weight = 0.5f;
                     r = (int)(r * weight + fg.getRed() * (1 - weight));
                     g = (int)(g * weight + fg.getGreen() * (1 - weight));
                     b = (int)(b * weight + fg.getBlue() * (1 - weight));
                     blurred.data[index] = (byte)b;
                     blurred.data[index + 1] = (byte)g;
                     blurred.data[index + 2] = (byte)r;
                  }
               }
            }

            image.dispose();
            image = new Image(e.widget.getDisplay(), blurred);
            gc = new GC(image);
            gc.setForeground(ResourceManager.getColor(0xfa, 0xfa, 0xff)); // not white, because that may be transparent
            //gc.setForeground(ResourceManager.getColor(0x54, 0x67, 0xc2)); // lightened blue
            gc.setFont(title.getFont());
            gc.drawText(getTitle(), 1, 0, true); // indent 1 pixel
            gc.dispose();
            e.gc.drawImage(image, 0, 0);
            image.dispose();
         }
      });
      title.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.VERTICAL_ALIGN_END));
      title.addListener(SWT.Resize, new Listener() {

         public void handleEvent( Event event ) {
            GridData gd = (GridData)title.getLayoutData();
            int hh = title.computeSize(title.getSize().x, SWT.DEFAULT).y + 1; // increase the label's height by 1 pixel 
            if ( hh != gd.heightHint ) {
               gd.heightHint = hh;
               title.getParent().layout();
            }
         }

      });
   }

   protected Point getSize() {
      return _shell.computeSize(getWidth(), getHeight());
   }

   protected void limitBounds( Rectangle displayBounds, Rectangle shellBounds ) {
      if ( displayBounds.x > shellBounds.x ) {
         shellBounds.x = displayBounds.x;
      }
      if ( displayBounds.y > shellBounds.y ) {
         shellBounds.y = displayBounds.y;
      }
      if ( displayBounds.x + displayBounds.width < shellBounds.x + shellBounds.width ) {
         shellBounds.x -= (shellBounds.x + shellBounds.width) - (displayBounds.x + displayBounds.width);
      }
      if ( displayBounds.y + displayBounds.height < shellBounds.y + shellBounds.height ) {
         shellBounds.y -= (shellBounds.y + shellBounds.height) - (displayBounds.y + (displayBounds.height - 30));
      }
   }

   protected void okTriggered() {
      ok();
      _shell.dispose();
      _transparentShell.dispose();
      Display.getCurrent().asyncExec(new Runnable() {

         public void run() {
            okPostDispose();
         }
      });
   }

   protected void setBounds() {
      Point size = getSize();
      Point pt = _transparentShell.getDisplay().getCursorLocation();
      Point location = new Point(pt.x - size.x / 2, pt.y - (int)(size.y * 0.7));
      setBounds(location);
   }

   protected void setBounds( Point location ) {
      Point size = getSize();
      Rectangle bounds = new Rectangle(location.x, location.y, size.x + (RESIZE_BORDER_X + 1) * 2, size.y + BG_COLORS.length * 2 + (RESIZE_BORDER_Y + 1) * 2);

      Point pt = _transparentShell.getDisplay().getCursorLocation();
      Monitor activeMonitor = null;
      for ( Monitor m : _transparentShell.getDisplay().getMonitors() ) {
         if ( m.getBounds().contains(pt) ) {
            activeMonitor = m;
         }
      }
      Rectangle monitorBounds = activeMonitor.getBounds();
      limitBounds(monitorBounds, bounds);

      _transparentShell.setBounds(bounds);
      setShellBounds(bounds);
      _transparentShell.setVisible(true);
      _shell.setVisible(true);
      _shell.open();
   }

   protected void setSize() {
      Point size = getSize();
      Rectangle bounds = new Rectangle(_transparentShell.getLocation().x, _transparentShell.getLocation().y, size.x + (RESIZE_BORDER_X + 1) * 2, size.y
         + BG_COLORS.length * 2 + (RESIZE_BORDER_Y + 1) * 2);
      _transparentShell.setBounds(bounds);
      setShellBounds(bounds);
   }

   private void init() {
      int onTop = (_style & SWT.ON_TOP) > 0 ? SWT.ON_TOP : 0;
      int resize = SWTUtils.IS_PLATFORM_WIN32 ? SWT.RESIZE : 0;
      _transparentShell = new Shell(Display.getDefault(), SWT.MODELESS | resize | onTop);

      _transparentShell.setBackgroundMode(SWT.INHERIT_FORCE);
      _transparentShellBackgroundPainter = new BackgroundPainter(_transparentShell, true);
      _transparentShell.addListener(SWT.Resize, _transparentShellBackgroundPainter);
      _transparentShell.addDisposeListener(new DisposeListener() {

         public void widgetDisposed( DisposeEvent e ) {
            if ( _backgroundImageTransparentShell != null ) {
               _backgroundImageTransparentShell.dispose();
            }
            if ( _backgroundImageShell != null ) {
               _backgroundImageShell.dispose();
            }
         }
      });
      _transparentShell.setAlpha(HEADER_FOOTER_ALPHA);

      _transparentShell.setText(getTitle());
      _transparentShell.setImages(getImages());

      _shell = _transparentShell;
      if ( SWTUtils.IS_PLATFORM_WIN32 ) {
         _shell = new Shell(_transparentShell, SWT.MODELESS | SWT.NO_TRIM | onTop);
         _shell.setBackgroundMode(SWT.INHERIT_FORCE);
         _shell.addListener(SWT.Resize, new BackgroundPainter(_shell, false));
         _shell.addDisposeListener(new DisposeListener() {

            public void widgetDisposed( DisposeEvent e ) {
               _transparentShell.dispose();
            }
         });
      }
      _shell.setLayout(new GridLayout());

      Composite header = new Composite(_shell, SWT.NONE);
      header.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL));
      GridLayout gridLayout = new GridLayout(2, false);
      gridLayout.marginHeight = 0;
      gridLayout.marginWidth = 0;
      gridLayout.marginTop = 0;
      gridLayout.marginBottom = 5;
      gridLayout.marginLeft = 5;
      gridLayout.marginRight = 0;
      header.setLayout(gridLayout);
      createTitle(header);

      if ( (_style & SWT.CANCEL) != 0 || (_style & SWT.OK) != 0 ) {
         ToolBar okCancelToolbar = new ToolBar(header, SWT.FLAT | SWT.HORIZONTAL);
         okCancelToolbar.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.VERTICAL_ALIGN_BEGINNING));

         if ( (_style & SWT.OK) != 0 ) {
            _ok = new ToolItem(okCancelToolbar, SWT.PUSH);
            _ok.setImage(IMAGE_OK);
            _ok.setHotImage(IMAGE_OK_HOT);
            _ok.setToolTipText("Bestätigen.");
            _ok.addSelectionListener(new SelectionAdapter() {

               @Override
               public void widgetSelected( SelectionEvent e ) {
                  okTriggered();
               }
            });
         }
         if ( (_style & SWT.CANCEL) != 0 ) {
            _cancel = new ToolItem(okCancelToolbar, SWT.PUSH);
            _cancel.setImage(IMAGE_CANCEL);
            _cancel.setHotImage(IMAGE_CANCEL_HOT);
            _cancel.setToolTipText("Abbrechen.");
            _cancel.addSelectionListener(new SelectionAdapter() {

               @Override
               public void widgetSelected( SelectionEvent e ) {
                  cancel();
                  _transparentShell.setData(null);
                  _shell.setData(null);
                  _transparentShell.dispose();
               }
            });

            _shell.addListener(SWT.Traverse, new Listener() {

               public void handleEvent( Event event ) {
                  if ( event.detail == SWT.TRAVERSE_ESCAPE ) {
                     cancel();
                  }
               }
            });
         }
      }

      String descriptionText = getDescriptionText();

      if ( descriptionText != null && descriptionText.length() > 0 ) {
         Label t = new Label(_shell, SWT.WRAP);
         t.setText(descriptionText);
         GridData gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL);
         gridData.horizontalIndent = 3;
         t.setLayoutData(gridData);
         //         t.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
      }

      createContent(_shell);

      setBounds();

      new DialogShellEasyMoveHandler(_transparentShell, new Composite[] { _shell });
      if ( _transparentShell != _shell ) {
         _transparentShell.addListener(SWT.Resize, new Listener() {

            public void handleEvent( Event event ) {
               setShellBounds(_transparentShell.getBounds());
            }
         });
      }
   }

   private void runEventLoop() {
      int maxWaitCycles = 100;
      while ( _transparentShell == null && --maxWaitCycles > 0 ) {
         try {
            Thread.sleep(10);
         }
         catch ( InterruptedException e ) {}
      }

      Display display = _transparentShell.getDisplay();

      while ( _transparentShell != null && !_transparentShell.isDisposed() ) {
         try {
            if ( !display.readAndDispatch() ) {
               display.sleep();
            }
         }
         catch ( Throwable e ) {}
      }
      try {
         display.update();
      }
      catch ( Exception e ) {
         // Invalid thread access exception possible and not critical 
      }
   }

   private void setShellBounds( Rectangle transparentShellBounds ) {
      transparentShellBounds.width = transparentShellBounds.width - (RESIZE_BORDER_X - 1) * 2;
      transparentShellBounds.height = transparentShellBounds.height - BG_COLORS.length * 2 - (RESIZE_BORDER_Y - 1) * 2;
      transparentShellBounds.x = transparentShellBounds.x + RESIZE_BORDER_X - 1;
      transparentShellBounds.y = transparentShellBounds.y + BG_COLORS.length + RESIZE_BORDER_Y - 1;
      _shell.setBounds(transparentShellBounds);
   }

   protected class BackgroundPainter implements Listener {

      private final boolean _drawHeaderFooter;
      private Shell         _shell;

      public BackgroundPainter( Shell shell, boolean drawHeaderFooter ) {
         _drawHeaderFooter = drawHeaderFooter;
         _shell = shell;
      }

      public void handleEvent( Event event ) {
         if ( _shell.isDisposed() ) {
            return;
         }
         Rectangle rect = _shell.getClientArea();
         Image newImage = new Image(_shell.getDisplay(), Math.max(1, rect.width), Math.max(1, rect.height));
         GC gc = new GC(newImage);
         gc.drawImage(BG_IMAGE, rect.width - BG_IMAGE.getBounds().width, rect.height - (_drawHeaderFooter ? BG_COLORS.length : 0) - 1
            - BG_IMAGE.getBounds().height);
         if ( _drawHeaderFooter ) {
            for ( int i = 0, length = BG_COLORS.length; i < length; i++ ) {
               gc.setForeground(BG_COLORS[i]);
               gc.drawLine(0, i, rect.width, i);
               gc.drawLine(0, rect.height - i - 1, rect.width, rect.height - i - 1);
            }
         }
         gc.dispose();
         _shell.setBackgroundImage(newImage);
         Image bg = _drawHeaderFooter ? _backgroundImageTransparentShell : _backgroundImageShell;
         if ( bg != null ) {
            bg.dispose();
         }
         if ( _drawHeaderFooter ) {
            _backgroundImageTransparentShell = newImage;
         } else {
            _backgroundImageShell = newImage;
         }
      }

   }

   private class DialogShellEasyMoveHandler extends EasyMoveHandler {

      int currentFadeAlpha = 255;
      int currentFadeStamp = 0;

      public DialogShellEasyMoveHandler( Shell shell, Composite[] draggableParents ) {
         super(shell, draggableParents);
      }

      @Override
      public void move( final MouseEvent e ) {
         currentFadeStamp = e.time;
         if ( currentFadeAlpha < 255 ) {
            _shell.setAlpha(255);
            _transparentShellBackgroundPainter.handleEvent(null);
         }
         currentFadeAlpha = 0;

         GC gc = new GC(_shell);
         Image screenshotImage = new Image(_shell.getDisplay(), _shell.getSize().x, _shell.getSize().y);
         gc.copyArea(screenshotImage, 0, 0);
         Image image = new Image(_shell.getDisplay(), screenshotImage.getImageData());
         screenshotImage.dispose();
         screenshotImage = image;
         gc.dispose();
         Image backgroundImage = new Image(_shell.getDisplay(), _backgroundImageTransparentShell.getBounds().width, _backgroundImageTransparentShell
               .getBounds().height);
         gc = new GC(backgroundImage);
         gc.drawImage(_backgroundImageTransparentShell, 0, 0);
         gc.setAlpha(50);
         gc.drawImage(screenshotImage, 0, BG_COLORS.length);
         gc.dispose();
         screenshotImage.dispose();
         _transparentShell.setBackgroundImage(backgroundImage);
         _backgroundImageTransparentShell.dispose();
         _backgroundImageTransparentShell = backgroundImage;

         _shell.setVisible(false);

         _transparentShell.redraw();
         _transparentShell.update();
         super.move(e);
         setShellBounds(_transparentShell.getBounds());

         _shell.setAlpha(0);
         _shell.setVisible(true);
         _shell.forceActive();
         _shell.getDisplay().asyncExec(new Runnable() {

            public void run() {
               if ( _shell.isDisposed() || currentFadeStamp != e.time ) {
                  return;
               }
               currentFadeAlpha += 20;
               if ( currentFadeAlpha < 255 ) {
                  _shell.setAlpha(currentFadeAlpha);
                  _shell.getDisplay().timerExec(33, this);
               } else {
                  _shell.setAlpha(255);
                  _transparentShellBackgroundPainter.handleEvent(null);
               }
            }
         });
      }
   }
}
