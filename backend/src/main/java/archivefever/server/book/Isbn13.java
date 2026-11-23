package archivefever.server.book;

/** ISBN-13 validation. Book barcodes are EAN-13 numbers starting with 978 or 979. */
public final class Isbn13 {

	private Isbn13() {
	}

	public static boolean isValid(String text) {
		if (text == null || !text.matches("97[89]\\d{10}")) {
			return false;
		}
		int sum = 0;
		for (int i = 0; i < 12; i++) {
			sum += (text.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
		}
		return (10 - sum % 10) % 10 == text.charAt(12) - '0';
	}

}
