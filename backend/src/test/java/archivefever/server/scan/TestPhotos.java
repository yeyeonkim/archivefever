package archivefever.server.scan;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.EAN13Writer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/** Synthetic "back cover" photos for tests. */
public final class TestPhotos {

	private TestPhotos() {
	}

	/** A pale cover with one EAN-13 barcode printed off-centre, as on a real book. */
	public static BufferedImage backCover(String ean13) {
		BufferedImage photo = new BufferedImage(900, 1300, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = photo.createGraphics();
		g.setColor(new Color(0xE9E2D2));
		g.fillRect(0, 0, photo.getWidth(), photo.getHeight());
		g.setColor(new Color(0x3A3A3A));
		g.fillRect(80, 120, 740, 6);
		g.fillRect(80, 160, 520, 6);
		g.setColor(Color.WHITE);
		g.fillRect(470, 980, 360, 220);
		g.drawImage(barcode(ean13, 300, 160), 500, 1010, null);
		g.dispose();
		return photo;
	}

	public static BufferedImage blank() {
		BufferedImage photo = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = photo.createGraphics();
		g.setColor(new Color(0xE9E2D2));
		g.fillRect(0, 0, photo.getWidth(), photo.getHeight());
		g.dispose();
		return photo;
	}

	/** The photo as a phone would deliver it sideways: turned a quarter. */
	public static BufferedImage sideways(BufferedImage photo) {
		BufferedImage turned = new BufferedImage(photo.getHeight(), photo.getWidth(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = turned.createGraphics();
		g.translate(0, photo.getWidth());
		g.rotate(-Math.PI / 2);
		g.drawImage(photo, 0, 0, null);
		g.dispose();
		return turned;
	}

	public static byte[] png(BufferedImage image) {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	private static BufferedImage barcode(String ean13, int width, int height) {
		BitMatrix bars = new EAN13Writer().encode(ean13, BarcodeFormat.EAN_13, width, height);
		BufferedImage image = new BufferedImage(bars.getWidth(), bars.getHeight(), BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < bars.getHeight(); y++) {
			for (int x = 0; x < bars.getWidth(); x++) {
				image.setRGB(x, y, bars.get(x, y) ? 0x000000 : 0xFFFFFF);
			}
		}
		return image;
	}

}
