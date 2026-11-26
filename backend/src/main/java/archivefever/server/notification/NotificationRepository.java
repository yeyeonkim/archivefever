package archivefever.server.notification;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findByUserIdOrderByIdDesc(String userId, Pageable page);

	/** The friend requests from one user that the other has not answered. */
	List<Notification> findByUserIdAndKindAndActorIdAndAnswerIsNull(String userId, NotificationService.Kind kind,
			String actorId);

	/** The requests for one book swap that have not been answered. */
	List<Notification> findByKindAndSwapIdAndAnswerIsNull(NotificationService.Kind kind, Long swapId);

}
