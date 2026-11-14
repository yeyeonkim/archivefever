package archivefever.ui.components;

import archivefever.ui.theme.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Keeps the design canvas intact while scaling both rendering and mouse input. */
public final class ScaledAppView extends JPanel {
    private final JComponent app;
    private final JComponent input = new JPanel();
    private double scale;
    private Component hovered;
    private Component pressed;
    private Component clickTarget;

    public ScaledAppView(JComponent app, double scale) {
        this.app = app;
        setLayout(null);
        setBackground(app.getBackground());
        add(app);
        input.setOpaque(false);
        input.setFocusable(false);
        add(input, 0);
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { updateHover(e); send(hovered,e,e.getID()); }
            @Override public void mouseEntered(MouseEvent e) { updateHover(e); }
            @Override public void mouseExited(MouseEvent e) {
                if (hovered != null) send(hovered,e,MouseEvent.MOUSE_EXITED);
                hovered = null;
                input.setCursor(Cursor.getDefaultCursor());
            }
            @Override public void mousePressed(MouseEvent e) {
                updateHover(e);
                pressed = hovered;
                clickTarget = pressed;
                send(pressed,e,e.getID());
            }
            @Override public void mouseDragged(MouseEvent e) {
                updateHover(e);
                send(pressed,e,e.getID());
            }
            @Override public void mouseReleased(MouseEvent e) {
                updateHover(e);
                Component target = pressed;
                pressed = null;
                send(target,e,e.getID());
                // A click may have changed the current screen.
                updateHover(e);
            }
            @Override public void mouseClicked(MouseEvent e) {
                // A button may open a modal on release. Finish the click on the
                // original target, never on the newly opened modal underneath.
                Component target=clickTarget;
                clickTarget=null;
                send(target,e,e.getID());
                updateHover(e);
            }
        };
        input.addMouseListener(mouse);
        input.addMouseMotionListener(mouse);
        setScale(scale);
    }

    public void setScale(double scale) {
        if (!Double.isFinite(scale) || scale <= 0 || scale > 1)
            throw new IllegalArgumentException("Scale must be in (0, 1]");
        this.scale = scale;
        setPreferredSize(new Dimension((int)Math.ceil(AppTheme.WIDTH*scale),
                (int)Math.ceil(AppTheme.HEIGHT*scale)));
        revalidate();
        repaint();
    }

    public double scale() { return scale; }

    @Override public void doLayout() {
        app.setBounds(0,0,AppTheme.WIDTH,AppTheme.HEIGHT);
        input.setBounds(0,0,getWidth(),getHeight());
    }

    @Override protected void paintChildren(Graphics graphics) {
        Graphics2D g = (Graphics2D)graphics.create();
        g.scale(scale,scale);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        app.paint(g);
        g.dispose();
    }

    // Descendant animations must repaint through the scaled coordinate system.
    @Override protected boolean isPaintingOrigin() { return true; }
    @Override public void paintImmediately(int x,int y,int width,int height) {
        super.paintImmediately(0,0,getWidth(),getHeight());
    }

    private Component targetAt(MouseEvent event) {
        int x=(int)Math.floor(event.getX()/scale);
        int y=(int)Math.floor(event.getY()/scale);
        if(x<0 || y<0 || x>=AppTheme.WIDTH || y>=AppTheme.HEIGHT) return null;
        return SwingUtilities.getDeepestComponentAt(app,x,y);
    }

    private void updateHover(MouseEvent event) {
        Component next = targetAt(event);
        if(next != hovered) {
            if(hovered != null) send(hovered,event,MouseEvent.MOUSE_EXITED);
            hovered = next;
            if(hovered != null) send(hovered,event,MouseEvent.MOUSE_ENTERED);
        }
        input.setCursor(hovered == null ? Cursor.getDefaultCursor() : hovered.getCursor());
    }

    private void send(Component target, MouseEvent event, int id) {
        if(target == null) return;
        Point point=SwingUtilities.convertPoint(app,
                (int)Math.floor(event.getX()/scale),
                (int)Math.floor(event.getY()/scale),target);
        target.dispatchEvent(new MouseEvent(target,id,event.getWhen(),event.getModifiersEx(),
                point.x,point.y,event.getXOnScreen(),event.getYOnScreen(),
                event.getClickCount(),event.isPopupTrigger(),event.getButton()));
        repaint();
    }
}
