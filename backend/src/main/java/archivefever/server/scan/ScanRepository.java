package archivefever.server.scan;

import org.springframework.data.jpa.repository.JpaRepository;

interface ScanRepository extends JpaRepository<Scan, String> {
}
