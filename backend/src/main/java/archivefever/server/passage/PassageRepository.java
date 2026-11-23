package archivefever.server.passage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface PassageRepository extends JpaRepository<Passage, Long> {

	List<Passage> findByUserIdAndIsbn13(String userId, String isbn13);

}
