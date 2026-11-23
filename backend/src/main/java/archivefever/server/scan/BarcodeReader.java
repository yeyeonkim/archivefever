package archivefever.server.scan;

import archivefever.server.book.Isbn13;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.Binarizer;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/** Finds the ISBN barcode in a photo of a book's back cover. */
@Component
public class BarcodeReader {

	private static final Map<DecodeHintType, Object> HINTS = Map.of(
			DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.EAN_13),
			DecodeHintType.TRY_HARDER, Boolean.TRUE);

	/** Larger photos are shrunk first: bars stay readable and decoding stays fast. */
	private static final int MAX_EDGE = 2000;

	private static final List<Function<LuminanceSource, Binarizer>> BINARIZERS = List.of(
			HybridBinarizer::new, GlobalHistogramBinarizer::new);

	public Optional<String> readIsbn13(BufferedImage photo) {
		BufferedImage full = fit(photo, MAX_EDGE);
		BufferedImage half = fit(photo, MAX_EDGE / 2);
		// ImageIO ignores the camera's orientation tag, so a portrait photo may arrive
		// sideways. Barcodes read the same in both directions along a row, which leaves
		// two orientations to try.
		for (BufferedImage image : List.of(full, half)) {
			for (BufferedImage oriented : List.of(image, quarterTurn(image))) {
				Optional<String> isbn = decode(oriented);
				if (isbn.isPresent()) {
					return isbn;
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<String> decode(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		LuminanceSource source = new RGBLuminanceSource(width, height, image.getRGB(0, 0, width, height, null, 0, width));
		for (Function<LuminanceSource, Binarizer> binarizer : BINARIZERS) {
			try {
				// A back cover can carry other barcodes (price stickers, the five-digit add-on
				// next to the ISBN), so look at every code found, not just the first.
				Result[] results = new GenericMultipleBarcodeReader(new MultiFormatReader())
						.decodeMultiple(new BinaryBitmap(binarizer.apply(source)), HINTS);
				for (Result result : results) {
					if (Isbn13.isValid(result.getText())) {
						return Optional.of(result.getText());
					}
				}
			}
			catch (NotFoundException exception) {
				// Try the next binarizer.
			}
		}
		return Optional.empty();
	}

	private static BufferedImage fit(BufferedImage image, int maxEdge) {
		double scale = Math.min(1, (double) maxEdge / Math.max(image.getWidth(), image.getHeight()));
		int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = result.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, 0, 0, width, height, null);
		g.dispose();
		return result;
	}

	private static BufferedImage quarterTurn(BufferedImage image) {
		BufferedImage result = new BufferedImage(image.getHeight(), image.getWidth(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = result.createGraphics();
		g.translate(image.getHeight(), 0);
		g.rotate(Math.PI / 2);
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return result;
	}

}
