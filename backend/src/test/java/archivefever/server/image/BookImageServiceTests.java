package archivefever.server.image;

import static org.assertj.core.api.Assertions.assertThat;

import archivefever.server.image.BookImageService.Kind;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class BookImageServiceTests {

	@Test
	void recognisesTheCataloguesStandInPicture() {
		BufferedImage standIn = new BufferedImage(420, 600, BufferedImage.TYPE_INT_RGB);

		assertThat(BookImageService.isPlaceholder(standIn, 4975, Kind.COVER)).isTrue();
		// A real cover can share those dimensions but is never that small a file.
		assertThat(BookImageService.isPlaceholder(standIn, 48_000, Kind.COVER)).isFalse();
		assertThat(BookImageService.isPlaceholder(new BufferedImage(708, 1200, BufferedImage.TYPE_INT_RGB), 90_000, Kind.COVER))
				.isFalse();
	}

	@Test
	void aSpineMustBeTallAndThin() {
		assertThat(BookImageService.isPlaceholder(new BufferedImage(58, 1200, BufferedImage.TYPE_INT_RGB), 20_000, Kind.SPINE))
				.isFalse();
		// A cover-shaped picture in the spine slot is a stand-in whatever its file size.
		assertThat(BookImageService.isPlaceholder(new BufferedImage(420, 600, BufferedImage.TYPE_INT_RGB), 48_000, Kind.SPINE))
				.isTrue();
	}

}
