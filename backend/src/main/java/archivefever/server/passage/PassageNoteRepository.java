package archivefever.server.passage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PassageNoteRepository extends JpaRepository<PassageNote, Long> {

	List<PassageNote> findByPassageIdInAndAuthorIdIn(Collection<Long> passageIds, Collection<String> authorIds);

	/** Everyone's marks on these passages. */
	List<PassageNote> findByPassageIdIn(Collection<Long> passageIds);

	Optional<PassageNote> findByPassageIdAndAuthorIdAndSentence(Long passageId, String authorId, int sentence);

}
