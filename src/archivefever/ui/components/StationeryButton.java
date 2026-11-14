package archivefever.ui.components;

import archivefever.ui.theme.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Figma x/y refer to the rotated bounding box, not the unrotated image. */
public final class StationeryButton extends JButton {
    private static final int PAD=32, RESOLUTION=2;
    private boolean active;
    private final String imagePath;
    private BufferedImage artwork;
    private boolean hovered;
    private float hoverLift;
    private final Timer hoverAnimation;
    private String geometry="";
    private double sceneX,sceneY,rotatedWidth,rotatedHeight,displayScale=1;

    public StationeryButton(String name,String imagePath) {
        this.imagePath=imagePath;
        setBorder(null);setOpaque(false);setContentAreaFilled(false);setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName(name);
        hoverAnimation=new Timer(15,e->{
            float target=hovered?6:0;
            hoverLift+=(target-hoverLift)*.3f;
            if(Math.abs(target-hoverLift)<.15f){hoverLift=target;((Timer)e.getSource()).stop();}
            repaint();
        });
        addMouseListener(new MouseAdapter(){
            @Override public void mouseEntered(MouseEvent e){hovered=true;hoverAnimation.start();}
            @Override public void mouseExited(MouseEvent e){hovered=false;hoverAnimation.start();}
        });
    }

    public void setSceneBounds(double x,double y,double w,double h,double angle,boolean flip) {
        sceneX=x;sceneY=y;
        double r=Math.toRadians(angle);
        rotatedWidth=Math.abs(w*Math.cos(r))+Math.abs(h*Math.sin(r));
        rotatedHeight=Math.abs(w*Math.sin(r))+Math.abs(h*Math.cos(r));
        String next=w+":"+h+":"+angle+":"+flip;
        if(next.equals(geometry))return;
        geometry=next;
        String path=imagePath.endsWith("/pencil.png") && flip
                ?"images/read/expanded-pencil.png":imagePath;
        BufferedImage source=AppTheme.image(path);
        artwork=new BufferedImage((int)Math.ceil((rotatedWidth+PAD*2)*RESOLUTION),
                (int)Math.ceil((rotatedHeight+PAD*2)*RESOLUTION),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=artwork.createGraphics();
        g.scale(RESOLUTION,RESOLUTION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(PAD+rotatedWidth/2,PAD+rotatedHeight/2);
        g.rotate(r);if(flip)g.scale(1,-1);
        g.translate(-w/2,-h/2);
        if(imagePath.endsWith("highlighter.png")) {
            // Figma's image-fill crop, not a stretch of the complete square asset.
            g.clip(new Rectangle2D.Double(0,0,w,h));
            AffineTransform fill=new AffineTransform();fill.translate(-.562*w,-.3951*h);
            fill.scale(2.1157*w/source.getWidth(),1.3951*h/source.getHeight());
            g.drawImage(source,fill,null);
        } else if(imagePath.endsWith("pencil.png") || imagePath.endsWith("hand-lens.png")) {
            if(imagePath.endsWith("hand-lens.png")) {
                g.setColor(new Color(217,217,217,51));g.fill(new Ellipse2D.Double(9,9,102,100));
            }
            AffineTransform image=AffineTransform.getScaleInstance((w+28)/source.getWidth(),(h+28)/source.getHeight());
            g.drawImage(source,image,null);
        } else SceneImages.cover(g,source,0,0,(int)Math.round(w),(int)Math.round(h));
        g.dispose();

    }

    public void layoutAt(double scale,double ox,double oy) {
        displayScale=scale;
        setBounds((int)Math.round(ox+(sceneX-PAD)*scale),
                (int)Math.round(oy+(sceneY-PAD)*scale),
                (int)Math.ceil((rotatedWidth+2*PAD)*scale),
                (int)Math.ceil((rotatedHeight+2*PAD)*scale));
    }
    public void setActive(boolean active){this.active=active;repaint();}
    public boolean isActive(){return active;}
    @Override public boolean contains(int x,int y) {
        return opaque(x/displayScale,y/displayScale+hoverLift) || opaque(x/displayScale,y/displayScale);
    }
    private boolean opaque(double x,double y) {
        if(artwork==null)return false;
        int px=(int)(x*RESOLUTION),py=(int)(y*RESOLUTION);
        return px>=0 && py>=0 && px<artwork.getWidth() && py<artwork.getHeight()
                && (artwork.getRGB(px,py)>>>24)>60;
    }
    @Override public void removeNotify(){hoverAnimation.stop();hovered=false;hoverLift=0;super.removeNotify();}
    @Override protected void paintComponent(Graphics graphics) {
        if(artwork==null)return;
        Graphics2D g=(Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        AffineTransform image=new AffineTransform();image.translate(0,-hoverLift*displayScale);
        image.scale(displayScale/RESOLUTION,displayScale/RESOLUTION);
        g.drawImage(artwork,image,null);g.dispose();
    }
}
