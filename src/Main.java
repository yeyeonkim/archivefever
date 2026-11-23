import archivefever.ui.navigation.AppShell;
import archivefever.ui.components.ScaledAppView;
import archivefever.ui.theme.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Archive Fever");
            AppShell app=new AppShell();
            // Uses the server named by -Darchivefever.api, if any; otherwise demo data.
            archivefever.app.Backend.attachDesktop(app);
            ScaledAppView view = new ScaledAppView(app, 1);
            frame.setContentPane(view);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setResizable(false);

            Timer snapTimer=new Timer(180,e -> {
                if(!app.isReading() || app.isReadingTransitionRunning()) return;
                boolean expanded=app.expandedModeAfterResize(frame);
                app.animateReadingSnap(frame,expanded);
            });
            snapTimer.setRepeats(false);
            frame.addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) {
                    if(app.isReading() && !app.isReadingTransitionRunning()) snapTimer.restart();
                }
            });
            frame.pack();

            GraphicsConfiguration display = frame.getGraphicsConfiguration();
            Rectangle bounds = display.getBounds();
            Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(display);
            Insets windowInsets = frame.getInsets();
            int availableWidth = bounds.width-screenInsets.left-screenInsets.right
                    -windowInsets.left-windowInsets.right-48;
            int availableHeight = bounds.height-screenInsets.top-screenInsets.bottom
                    -windowInsets.top-windowInsets.bottom-48;
            // Cap the preview at 800px tall; smaller displays shrink proportionally.
            double scale = Math.min(1, Math.min(
                    (double)Math.min(800,availableHeight)/AppTheme.HEIGHT,
                    (double)availableWidth/AppTheme.WIDTH));
            view.setScale(scale);
            frame.pack();
            frame.setLocation(
                    bounds.x+screenInsets.left+(bounds.width-screenInsets.left-screenInsets.right-frame.getWidth())/2,
                    bounds.y+screenInsets.top+(bounds.height-screenInsets.top-screenInsets.bottom-frame.getHeight())/2);
            frame.setVisible(true);
        });
    }
}
