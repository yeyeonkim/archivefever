package archivefever.server.scan;

/**
 * Life of a scan. It starts PENDING while the phone takes the photo; the upload moves it
 * to exactly one outcome, and a RESOLVED scan becomes CONFIRMED when the user accepts
 * the book. Every state except PENDING and RESOLVED is final.
 */
public enum ScanStatus {

	/** Created; waiting for the photo. */
	PENDING,

	/** The barcode was read and the catalogue knows the book. */
	RESOLVED,

	/** The user accepted the book and it is in their library. */
	CONFIRMED,

	/** No ISBN barcode could be read from the photo. */
	NO_BARCODE,

	/** The ISBN was read but the catalogue does not list it. */
	NOT_FOUND,

	/** The catalogue could not be asked; the reason says why. */
	FAILED,

	/** The user backed out before sending a photo. */
	CANCELLED,

	/** No photo arrived in time. */
	EXPIRED

}
