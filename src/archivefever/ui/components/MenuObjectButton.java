package archivefever.ui.components;

import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;

/** Original illustration and native label share one alpha-aware 12px hover target. */
public final class MenuObjectButton extends JButton {
    private static final int PAD=28,LIFT=12,RES=2;
    private final BufferedImage artwork;
    private final String label;
    private final int labelX,labelY;
    private final Timer animation;
    private float lift;
    private boolean hovered;

    public MenuObjectButton(String label,String asset,double x,double y,double width,double height,
                            double angle,int textX,int textY,boolean idCard,Runnable action) {
        this.label=label;
        FontMetrics fm=getFontMetrics(AppFonts.headingRegular(20));
        double radians=Math.toRadians(angle);
        double bw=Math.abs(width*Math.cos(radians))+Math.abs(height*Math.sin(radians));
        double bh=Math.abs(width*Math.sin(radians))+Math.abs(height*Math.cos(radians));
        int left=(int)Math.floor(Math.min(x,textX))-PAD,top=(int)Math.floor(Math.min(y,textY))-PAD;
        int w=(int)Math.ceil(Math.max(x+bw,textX+fm.stringWidth(label))-left)+PAD;
        int h=(int)Math.ceil(Math.max(y+bh,textY+24)-top)+PAD;
        labelX=textX-left;labelY=textY-top;
        setBounds(left,top-LIFT,w,h+LIFT);
        BufferedImage source=AppTheme.image("images/menu/"+asset+".png");
        BufferedImage shape=new BufferedImage(w*RES,h*RES,BufferedImage.TYPE_INT_ARGB);
        Graphics2D image=shape.createGraphics();image.scale(RES,RES);
        image.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        image.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        image.translate(x+bw/2-left,y+bh/2-top);image.rotate(radians);image.translate(-width/2,-height/2);
        SceneImages.cover(image,source,0,0,(int)Math.round(width),(int)Math.round(height));image.dispose();
        if(idCard){
            Graphics2D mask=shape.createGraphics();mask.scale(RES,RES);
            mask.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            mask.translate(193+40.708/2-left,544+21.213/2-top);mask.rotate(Math.toRadians(18.77));
            mask.setColor(Color.BLACK);mask.fill(new Rectangle2D.Double(-20,-8.812/2,40,8.812));mask.dispose();
        }
        BufferedImage shadow=new BufferedImage(w*RES,h*RES,BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg=shadow.createGraphics();sg.drawImage(shape,0,0,null);sg.setComposite(AlphaComposite.SrcIn);
        sg.setColor(new Color(0,0,0,64));sg.fillRect(0,0,shadow.getWidth(),shadow.getHeight());sg.dispose();
        float[] blur=new float[25];java.util.Arrays.fill(blur,1f/25);
        shadow=new ConvolveOp(new Kernel(25,1,blur),ConvolveOp.EDGE_NO_OP,null).filter(shadow,null);
        shadow=new ConvolveOp(new Kernel(1,25,blur),ConvolveOp.EDGE_NO_OP,null).filter(shadow,null);
        artwork=new BufferedImage(w*RES,h*RES,BufferedImage.TYPE_INT_ARGB);
        Graphics2D merged=artwork.createGraphics();merged.drawImage(shadow,16*RES,16*RES,null);
        merged.drawImage(shape,0,0,null);merged.dispose();
        setBorder(null);setOpaque(false);setContentAreaFilled(false);setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));setToolTipText(label);
        setActionCommand(label);getAccessibleContext().setAccessibleName(label);
        addActionListener(e->action.run());
        animation=new Timer(15,e->{
            float target=hovered?LIFT:0;lift+=(target-lift)*.25f;
            if(Math.abs(target-lift)<.15f){lift=target;((Timer)e.getSource()).stop();}repaint();
        });
        addMouseListener(new MouseAdapter(){
            @Override public void mouseEntered(MouseEvent e){hovered=true;animation.start();}
            @Override public void mouseExited(MouseEvent e){hovered=false;animation.start();}
        });
    }
    public float liftAmount(){return lift;}
    public void resetHover(){animation.stop();hovered=false;lift=0;repaint();}
    @Override public void removeNotify(){resetHover();super.removeNotify();}
    @Override public boolean contains(int x,int y){return hit(x,y-LIFT+Math.round(lift)) || hit(x,y-LIFT);}
    private boolean hit(int x,int y){
        FontMetrics fm=getFontMetrics(AppFonts.headingRegular(20));
        if(x>=labelX && x<labelX+fm.stringWidth(label) && y>=labelY && y<labelY+24)return true;
        int px=x*RES,py=y*RES;
        return px>=0 && py>=0 && px<artwork.getWidth() && py<artwork.getHeight() && (artwork.getRGB(px,py)>>>24)>80;
    }
    @Override protected void paintComponent(Graphics graphics){
        Graphics2D g=(Graphics2D)graphics.create();float offset=LIFT-lift;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        AffineTransform image=new AffineTransform();image.translate(0,offset);image.scale(1.0/RES,1.0/RES);
        g.drawImage(artwork,image,null);
        g.setFont(AppFonts.headingRegular(20));
        Shape letters=g.getFont().createGlyphVector(g.getFontRenderContext(),label)
                .getOutline(labelX,labelY+17+offset);
        g.setStroke(new BasicStroke(3));g.setColor(new Color(150,148,148,170));g.draw(letters);
        g.setColor(Color.WHITE);g.fill(letters);
        int textWidth=g.getFontMetrics().stringWidth(label);
        g.setStroke(new BasicStroke(.65f));
        g.draw(new Line2D.Float(labelX,labelY+19+offset,labelX+textWidth,labelY+19+offset));
        if(hasFocus())g.draw(new Rectangle2D.Float(labelX-2,labelY+offset,textWidth+4,24));
        g.dispose();
    }
}
