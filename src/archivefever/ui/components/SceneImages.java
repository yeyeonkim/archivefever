package archivefever.ui.components;

import java.awt.*;
import java.awt.image.BufferedImage;

public final class SceneImages {
    private SceneImages() {}

    public static void cover(Graphics2D g, BufferedImage image,
                             int x, int y, int width, int height) {
        double scale = Math.max((double)width/image.getWidth(), (double)height/image.getHeight());
        int w=(int)Math.round(image.getWidth()*scale), h=(int)Math.round(image.getHeight()*scale);
        Graphics2D clipped=(Graphics2D)g.create();
        clipped.clipRect(x,y,width,height);
        clipped.drawImage(image,x+(width-w)/2,y+(height-h)/2,w,h,null);
        clipped.dispose();
    }
}
