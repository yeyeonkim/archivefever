package archivefever.server.library;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface LibraryBookRepository extends JpaRepository<LibraryBook, Long> {

	List<LibraryBook> findByUserIdOrderByAddedAtAscIdAsc(String userId);

	boolean existsByUserIdAndIsbn13(String userId, String isbn13);

	Optional<LibraryBook> findByUserIdAndIsbn13(String userId, String isbn13);

}
