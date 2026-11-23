package archivefever.model;

import java.awt.image.BufferedImage;
import java.util.List;

public record BookSwap(String id, String friendId, String friendHandle, String bookTitle, String author,
                       BufferedImage cover, String passage, List<String> comments) {}
