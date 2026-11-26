package archivefever.ui.screens;

import archivefever.ui.components.SceneImages;
import archivefever.ui.theme.*;
import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Status text, back link, background requests and drawing shared by the friend and swap screens. */
abstract class SocialPanel extends JPanel {
    private static final Color ERROR=new Color(0x9B2D20);
    private static final BufferedImage SLIP=AppTheme.image("images/main/book-popup-paper.png");
    private static final int STATUS_X=51, STATUS_WIDTH=311, RETRY_X=302, RETRY_SPACE=52, NOTICE_MILLIS=3000;
    private boolean slip;
    private final JLabel status=new JLabel("",SwingConstants.CENTER) {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g=(Graphics2D)graphics.create();smooth(g);
            if(slip) {
                g.setColor(Color.WHITE);g.fillRect(0,0,getWidth(),getHeight());
                SceneImages.cover(g,SLIP,0,0,getWidth(),getHeight());
            }
            super.paintComponent(g);g.dispose();
        }
    };
    private final JButton retry=link("retry","다시 시도",RETRY_X,0,48,24,AppFonts.headingRegular(20),AppColors.GRAY);
    private final JButton back=link("back","뒤로가기",14,10,56,28,typewriter(20),Color.BLACK);
    private final Timer noticeTimer=new Timer(NOTICE_MILLIS,e->setMessage(""));
    private Runnable backAction;
    private Runnable lastRequest;
    private int generation;
    private boolean busy;

    SocialPanel(String name) {
        setLayout(null);setBackground(AppColors.WALL);setName(name);
        getAccessibleContext().setAccessibleName(name);
        noticeTimer.setRepeats(false);
        retry.setVisible(false);retry.addActionListener(e->{if(lastRequest!=null)lastRequest.run();});add(retry);
        status.setVisible(false);add(status);
        back.setVisible(false);back.addActionListener(e->{if(backAction!=null)backAction.run();});add(back);
    }

    final void setBackAction(Runnable action) {backAction=action;back.setVisible(action!=null);}
    final boolean isBusy() {return busy;}
    final String statusText() {return status.getText();}

    /** Places status text on the screen's paper, or on a paper slip where the screen has none. */
    final void setStatusArea(int top,boolean onSlip) {
        slip=onSlip;
        int height=onSlip?44:36;
        status.setBounds(STATUS_X,top,STATUS_WIDTH,height);
        retry.setLocation(RETRY_X,top+(height-retry.getHeight())/2);
        repaint();
    }

    final void setMessage(String message) {
        noticeTimer.stop();retry.setVisible(false);status.setBorder(null);
        boolean korean=message.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HANGUL);
        status.setFont(korean?AppFonts.body(15):AppFonts.headingRegular(24));
        status.setForeground(AppColors.GRAY);status.setText(message);status.setVisible(!message.isEmpty());
        repaint();
    }

    /** Shows a completed action briefly, then clears it. */
    final void showNotice(String message) {setMessage(message);noticeTimer.restart();}

    /** Runs work off the EDT; a newer request or reset makes older results stale. */
    final <T> void request(String loading,Callable<T> work,Consumer<T> onSuccess) {
        int current=++generation;
        busy=true;lastRequest=()->request(loading,work,onSuccess);
        setMessage(loading);
        new SwingWorker<T,Void>() {
            @Override protected T doInBackground() throws Exception {return work.call();}
            @Override protected void done() {
                if(current!=generation)return;
                busy=false;
                try {
                    T result=get();
                    setMessage("");
                    onSuccess.accept(result);
                } catch(ExecutionException e) {
                    showError(e.getCause()==null?"request failed":e.getCause().getMessage());
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();showError("request canceled");
                }
                repaint();
            }
        }.execute();
    }

    final void cancelRequests() {generation++;busy=false;lastRequest=null;setMessage("");}

    private void showError(String message) {
        setMessage(message);
        status.setForeground(ERROR);
        if(lastRequest!=null) {
            status.setBorder(BorderFactory.createEmptyBorder(0,0,0,RETRY_SPACE));
            retry.setVisible(true);
        }
    }

    /** Transparent click target over artwork that the panel paints itself. */
    static JButton hit(String name,int x,int y,int width,int height,Runnable action) {
        JButton button=new JButton() {
            @Override protected void paintComponent(Graphics graphics) {
                if(!hasFocus())return;
                graphics.setColor(new Color(0x857A6A));graphics.drawRect(0,0,getWidth()-1,getHeight()-1);
            }
        };
        button.setBounds(x,y,width,height);button.setBorder(null);button.setOpaque(false);
        button.setContentAreaFilled(false);button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getAccessibleContext().setAccessibleName(name);
        button.addActionListener(e->action.run());
        return button;
    }

    static JButton link(String text,String name,int x,int y,int width,int height,Font font,Color color) {
        JButton button=new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g=(Graphics2D)graphics.create();
                smooth(g);g.setFont(getFont());g.setColor(getForeground());
                FontMetrics metrics=g.getFontMetrics();
                int baseline=(getHeight()-metrics.getHeight())/2+metrics.getAscent();
                g.drawString(getText(),0,baseline);
                g.drawLine(0,baseline+2,Math.min(getWidth()-1,metrics.stringWidth(getText())),baseline+2);
                g.dispose();
            }
        };
        button.setBounds(x,y,width,height);button.setFont(font);button.setForeground(color);
        button.setBorder(null);button.setOpaque(false);button.setContentAreaFilled(false);button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getAccessibleContext().setAccessibleName(name);
        return button;
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    static Font typewriter(float size) {
        return AppFonts.typewriter(size).deriveFont(Map.of(TextAttribute.TRACKING,-.02f));
    }

    /** The typewriter and heading faces have no Korean letters; text with any is set in the body face. */
    static boolean korean(String text) {
        return text.codePoints().anyMatch(c->Character.UnicodeScript.of(c)==Character.UnicodeScript.HANGUL);
    }

    /** The font made smaller, if need be, for the text to fit the width. */
    static Font fitted(Graphics2D g,String text,Font font,float width) {
        float natural=text.isEmpty()?0:textWidth(g,text,font);
        return natural>width?font.deriveFont(font.getSize2D()*width/natural):font;
    }

    /** Draws a line of text whose CSS box (line-height 1) starts at top; returns its width. */
    static float drawText(Graphics2D g,String text,Font font,Color color,float x,float top,boolean underline) {
        TextLayout line=new TextLayout(text,font,g.getFontRenderContext());
        float baseline=top+(font.getSize2D()-line.getAscent()-line.getDescent())/2+line.getAscent();
        g.setColor(color);line.draw(g,x,baseline);
        if(underline) {
            g.setStroke(new BasicStroke(1f));
            g.draw(new java.awt.geom.Line2D.Float(x,baseline+2,x+line.getAdvance(),baseline+2));
        }
        return line.getAdvance();
    }

    static float textWidth(Graphics2D g,String text,Font font) {
        return new TextLayout(text,font,g.getFontRenderContext()).getAdvance();
    }

    /** Draws an image with the blurred, offset drop shadow Figma specifies. */
    static void drawShadowed(Graphics2D g,BufferedImage image,int x,int y,int width,int height,
                             int dx,int dy,int blur,float alpha) {
        int pad=blur*2;
        BufferedImage shadow=new BufferedImage(width+pad*2,height+pad*2,BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg=shadow.createGraphics();
        sg.drawImage(image,pad,pad,width,height,null);
        sg.setComposite(AlphaComposite.SrcIn);
        sg.setColor(new Color(0,0,0,Math.round(alpha*255)));sg.fillRect(0,0,shadow.getWidth(),shadow.getHeight());sg.dispose();
        int size=blur|1;
        float[] kernel=new float[size];java.util.Arrays.fill(kernel,1f/size);
        shadow=new ConvolveOp(new Kernel(size,1,kernel),ConvolveOp.EDGE_NO_OP,null).filter(shadow,null);
        shadow=new ConvolveOp(new Kernel(1,size,kernel),ConvolveOp.EDGE_NO_OP,null).filter(shadow,null);
        g.drawImage(shadow,x+dx-pad,y+dy-pad,null);
        g.drawImage(image,x,y,width,height,null);
    }

    abstract void paintScene(Graphics2D g);

    @Override protected final void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g=(Graphics2D)graphics.create();
        smooth(g);paintScene(g);
        g.dispose();
    }
}
