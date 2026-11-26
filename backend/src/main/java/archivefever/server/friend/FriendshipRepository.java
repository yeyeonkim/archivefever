package archivefever.server.friend;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface FriendshipRepository extends JpaRepository<Friendship, Long> {

	Optional<Friendship> findByLowIdAndHighId(String lowId, String highId);

	/** Every friendship and waiting request the user is part of; pass the user's id twice. */
	List<Friendship> findByLowIdOrHighId(String lowId, String highId);

}
