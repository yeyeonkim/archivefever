package archivefever.web;

import archivefever.app.Backend;
import archivefever.host.WebHost;
import archivefever.ui.components.ScaledAppView;
import archivefever.ui.navigation.AppShell;
import archivefever.ui.theme.AppTheme;
import javax.swing.*;
import java.awt.*;

/**
 * Entry point for the browser JVM inside the phone app's web view. The window has no
 * title bar and always fills the display the host page gives it; the 412x917 design is
 * scaled down to fit and centred.
 */
public final class WebMain {
    private static final Color BACKDROP=new Color(0x2B211A);
    /** Set by the host page with "?web.debug=true" to log the layout to the browser console. */
    private static final boolean DEBUG=Boolean.getBoolean("archivefever.web.debug");

    private WebMain() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(()->{
            AppShell app=new AppShell();
            app.setEmbedded(true);
            Backend.attachWeb(app);
            ScaledAppView view=new ScaledAppView(app,1);
            JPanel stage=new JPanel(null) {
                @Override public void doLayout() {
                    double scale=Math.min(1,Math.min((double)getWidth()/AppTheme.WIDTH,(double)getHeight()/AppTheme.HEIGHT));
                    if(scale<=0)return;
                    if(scale!=view.scale())view.setScale(scale);
                    Dimension size=view.getPreferredSize();
                    view.setBounds((getWidth()-size.width)/2,(getHeight()-size.height)/2,size.width,size.height);
                    if(DEBUG)System.out.println("[web] stage "+getWidth()+"x"+getHeight()+" scale "+scale+" view "+view.getBounds());
                }
            };
            stage.setBackground(BACKDROP);
            stage.add(view);

            // In the browser JVM, Swing's own double buffering paints the design a second
            // time at full size over the scaled view. The browser canvas already shows
            // only finished frames, so Swing's buffer is not needed here.
            RepaintManager.currentManager(app).setDoubleBufferingEnabled(false);

            JFrame frame=new JFrame("Archive Fever");
            frame.setUndecorated(true);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setContentPane(stage);
            Rectangle display=frame.getGraphicsConfiguration().getBounds();
            frame.setBounds(display);
            // Lay the design out at its final scale before the first paint.
            frame.validate();
            frame.setVisible(true);
            if(DEBUG)System.out.println("[web] display "+display+" frame "+frame.getBounds());
            // The display follows the page (rotation, on-screen keyboard); follow it in turn.
            new Timer(400,event->{
                Rectangle now=frame.getGraphicsConfiguration().getBounds();
                if(now.equals(frame.getBounds()))return;
                if(DEBUG)System.out.println("[web] display now "+now);
                frame.setBounds(now);
                frame.validate();
                frame.repaint();
            }).start();
            WebHost.announceReady();
        });
    }
}
