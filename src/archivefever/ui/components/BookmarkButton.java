package archivefever.ui.components;

import archivefever.ui.theme.AppFonts;
import archivefever.ui.theme.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.function.Consumer;
import archivefever.ui.navigation.Destination;

/** A stable hit area with an independently animated, alpha-aware bookmark. */
public final class BookmarkButton extends JButton {
    private static final int LIFT=24;
    private static final int RASTER_SCALE=2;
    private final BufferedImage artwork;
    private final Timer animation;
    private final Destination destination;
    private final int textX, textY;
    private final String[] lines;
    private final Color textColor;
    private float lift;
    private boolean hovered;
    private boolean focusLiftEnabled=true;

    public BookmarkButton(Destination destination, String asset, int x, int y,
                          int boxWidth, int boxHeight, double width, double height,
                          double angle, int labelX, int labelY, Color textColor,
                          String[] lines, boolean atlas, Consumer<Destination> navigate) {
        this.destination=destination;
        this.textX=labelX-x;
        this.textY=labelY-y;
        this.lines=lines.clone();
        this.textColor=textColor;
        artwork=render(asset,boxWidth,boxHeight,width,height,angle,atlas);
        setBounds(x,y-750-LIFT,artwork.getWidth()/RASTER_SCALE,
                artwork.getHeight()/RASTER_SCALE+LIFT);
        setBorder(null);
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(destination.label());
        setActionCommand(destination.name());
        getAccessibleContext().setAccessibleName(destination.label());
        addActionListener(e -> navigate.accept(destination));
        animation=new Timer(15,e -> {
            float target=hovered || (focusLiftEnabled && hasFocus())? LIFT:0;
            lift+=(target-lift)*.25f;
            if(Math.abs(target-lift)<.3f) {lift=target; ((Timer)e.getSource()).stop();}
            repaint();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {hovered=true;animate();}
            @Override public void mouseExited(MouseEvent e) {hovered=false;animate();}
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {focusLiftEnabled=true;animate();}
            @Override public void focusLost(FocusEvent e) {animate();}
        });
    }

    Destination destination() {return destination;}

    void resetLift() {
        hovered=false;
        focusLiftEnabled=false;
        animate();
    }

    private void animate() {if(!animation.isRunning()) animation.start();}

    @Override public void removeNotify() {
        animation.stop(); lift=0; hovered=false; super.removeNotify();
    }

    @Override public boolean contains(int x,int y) {
        // Keep the rest-position area active while lifting, preventing hover flicker.
        return opaqueAt(x,y-(LIFT-Math.round(lift))) || opaqueAt(x,y-LIFT);
    }

    private boolean opaqueAt(int x,int y) {
        // Labels are now native text, so include their visible area in hit testing.
        FontMetrics metrics=getFontMetrics(AppFonts.heading(20));
        for(int i=0;i<lines.length;i++) {
            int top=textY+i*20;
            if(x>=textX && x<textX+metrics.stringWidth(lines[i])
                    && y>=top && y<top+20) return true;
        }
        int px=x*RASTER_SCALE, py=y*RASTER_SCALE;
        return px>=0 && py>=0 && px<artwork.getWidth() && py<artwork.getHeight()
                && (artwork.getRGB(px,py)>>>24)>70;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();
        int offset=LIFT-Math.round(lift);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.drawImage(artwork,0,offset,artwork.getWidth()/RASTER_SCALE,
                artwork.getHeight()/RASTER_SCALE,null);
        // Draw text at the display's native resolution, rather than enlarging bitmap text.
        g.setFont(AppFonts.heading(20));g.setColor(textColor);
        for(int i=0;i<lines.length;i++) g.drawString(lines[i],textX,textY+offset+17+i*20);
        if(hasFocus()) {
            g.setColor(new Color(255,255,255,180));
            g.setStroke(new BasicStroke(2));
            // A visible keyboard-focus marker near the bookmark's exposed top.
            g.drawLine(Math.max(4,artwork.getWidth()/RASTER_SCALE/2-12),offset+8,
                    artwork.getWidth()/RASTER_SCALE/2+12,offset+8);
        }
        g.dispose();
    }

    private static BufferedImage render(String asset,int bw,int bh,double w,double h,
                                        double angle,boolean atlas) {
        BufferedImage source=AppTheme.image("images/navigation/"+asset+".png");
        BufferedImage shape=new BufferedImage((bw+32)*RASTER_SCALE,(bh+32)*RASTER_SCALE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=shape.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.scale(RASTER_SCALE,RASTER_SCALE);
        g.translate(bw/2.0,bh/2.0);
        g.rotate(Math.toRadians(angle));
        g.translate(-w/2,-h/2);
        if(atlas) {
            g.clip(new java.awt.geom.Rectangle2D.Double(0,0,w,h));
            g.drawImage(source,(int)Math.round(-3.1713*w),0,
                    (int)Math.round(5.5556*w),(int)Math.round(2.0253*h),null);
        } else SceneImages.cover(g,source,0,0,(int)Math.round(w),(int)Math.round(h));
        g.dispose();

        BufferedImage result=new BufferedImage((bw+32)*RASTER_SCALE,(bh+32)*RASTER_SCALE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D out=result.createGraphics();
        // Soft shadow follows the exported image's silhouette.
        BufferedImage shadow=new BufferedImage((bw+32)*RASTER_SCALE,(bh+32)*RASTER_SCALE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg=shadow.createGraphics();
        sg.drawImage(shape,0,0,null);
        sg.setComposite(AlphaComposite.SrcIn);
        sg.setColor(new Color(0,0,0,64));sg.fillRect(0,0,shadow.getWidth(),shadow.getHeight());sg.dispose();
        int blur=15*RASTER_SCALE+1;
        float[] kernel=new float[blur];java.util.Arrays.fill(kernel,1f/blur);
        shadow=new ConvolveOp(new Kernel(blur,1,kernel),ConvolveOp.EDGE_NO_OP,null).filter(shadow,null);
        shadow=new ConvolveOp(new Kernel(1,blur,kernel),ConvolveOp.EDGE_NO_OP,null).filter(shadow,null);
        out.drawImage(shadow,12*RASTER_SCALE,12*RASTER_SCALE,null);
        out.drawImage(shape,0,0,null);
        out.dispose();
        return result;
    }
}
