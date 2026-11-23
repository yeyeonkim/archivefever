package archivefever.ui.components;

import archivefever.ui.theme.AppTheme;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

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

    /** Reads the first path (M/L/C/Z) of an exported SVG, keeping the original geometry. */
    public static Shape svgPath(String resource) {
        try (InputStream in = AppTheme.resource(resource)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Element element = (Element)factory.newDocumentBuilder().parse(in)
                    .getElementsByTagName("path").item(0);
            Matcher matcher = Pattern.compile("[MLCZ]|[-+]?(?:[0-9]*[.][0-9]+|[0-9]+)(?:[eE][-+]?[0-9]+)?")
                    .matcher(element.getAttribute("d"));
            List<String> tokens = new ArrayList<>();
            while (matcher.find()) tokens.add(matcher.group());
            Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
            int i = 0;
            while (i < tokens.size()) {
                String command = tokens.get(i++);
                switch (command) {
                    case "M" -> {
                        path.moveTo(number(tokens,i),number(tokens,i+1)); i += 2;
                    }
                    case "L" -> {
                        path.lineTo(number(tokens,i),number(tokens,i+1)); i += 2;
                    }
                    case "C" -> {
                        path.curveTo(number(tokens,i),number(tokens,i+1),
                                number(tokens,i+2),number(tokens,i+3),
                                number(tokens,i+4),number(tokens,i+5)); i += 6;
                    }
                    case "Z" -> path.closePath();
                    default -> throw new IllegalArgumentException("Unsupported SVG path: " + command);
                }
            }
            return path;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load SVG path: " + resource, e);
        }
    }

    private static double number(List<String> tokens, int index) {
        return Double.parseDouble(tokens.get(index));
    }
}
