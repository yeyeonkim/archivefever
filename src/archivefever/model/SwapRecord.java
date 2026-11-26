package archivefever.model;

import java.util.List;

/**
 * What the reading page of a book swap shows: the passages both readers collected, the
 * friend's memos on them, and the user's own memos, which the page lets them write.
 * @param document the passages, with the user's own memos as its marks
 * @param comments the friend's memos, each as "@handle : memo"
 * @param memoAuthor the user's handle, with the "@", shown before their own memos
 * @param emptyNotice shown in place of the text while neither reader has collected anything
 */
public record SwapRecord(ReadingDocument document,List<String> comments,String memoAuthor,String emptyNotice) {}
