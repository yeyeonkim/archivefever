package archivefever.server.passage;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PassageRepository extends JpaRepository<Passage, Long> {

	List<Passage> findByUserIdAndIsbn13(String userId, String isbn13);

	List<Passage> findByUserIdInAndIsbn13(Collection<String> userIds, String isbn13);

}
