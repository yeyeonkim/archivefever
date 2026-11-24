package archivefever.server.auth;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface LoginSessionRepository extends JpaRepository<LoginSession, String> {

	@Transactional
	long deleteByExpiresAtBefore(Instant time);

}
