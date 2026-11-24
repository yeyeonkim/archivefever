package archivefever.server.book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Isbn13Tests {

	@Test
	void acceptsBookNumbersWithACorrectCheckDigit() {
		assertThat(Isbn13.isValid("9788936434120")).isTrue();
		assertThat(Isbn13.isValid("9791100000007")).isTrue();
	}

	@Test
	void rejectsAWrongCheckDigit() {
		assertThat(Isbn13.isValid("9788936434121")).isFalse();
		assertThat(Isbn13.isValid("9791100000001")).isFalse();
	}

	@Test
	void rejectsBarcodesThatAreNotBooks() {
		// A valid EAN-13 for a grocery item is not an ISBN.
		assertThat(Isbn13.isValid("8801234567893")).isFalse();
		// The five-digit add-on printed next to Korean ISBNs.
		assertThat(Isbn13.isValid("03810")).isFalse();
		assertThat(Isbn13.isValid("978893643412")).isFalse();
		assertThat(Isbn13.isValid("97889364341200")).isFalse();
		assertThat(Isbn13.isValid("97889364341X0")).isFalse();
		assertThat(Isbn13.isValid(null)).isFalse();
	}

}
