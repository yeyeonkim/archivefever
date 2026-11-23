package archivefever.server.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BarcodeReaderTests {

	private static final String ISBN = "9788936434120";

	private final BarcodeReader reader = new BarcodeReader();

	@Test
	void readsTheIsbnFromABackCoverPhoto() {
		assertThat(reader.readIsbn13(TestPhotos.backCover(ISBN))).hasValue(ISBN);
	}

	@Test
	void readsAPhotoThatArrivesSideways() {
		assertThat(reader.readIsbn13(TestPhotos.sideways(TestPhotos.backCover(ISBN)))).hasValue(ISBN);
	}

	@Test
	void readsALargeCameraPhoto() {
		// Phone cameras deliver far more pixels than the barcode needs.
		BufferedImage photo = TestPhotos.backCover(ISBN);
		BufferedImage large = new BufferedImage(photo.getWidth() * 4, photo.getHeight() * 4, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = large.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(photo, 0, 0, large.getWidth(), large.getHeight(), null);
		g.dispose();

		assertThat(reader.readIsbn13(large)).hasValue(ISBN);
	}

	@Test
	void findsNothingInAPhotoWithoutABarcode() {
		assertThat(reader.readIsbn13(TestPhotos.blank())).isEmpty();
	}

	@Test
	void ignoresBarcodesThatAreNotBooks() {
		assertThat(reader.readIsbn13(TestPhotos.backCover("8801234567893"))).isEmpty();
	}

}
