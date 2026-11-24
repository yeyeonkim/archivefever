package archivefever.server.book;

import org.springframework.data.jpa.repository.JpaRepository;

interface CachedBookRepository extends JpaRepository<CachedBook, String> {
}
