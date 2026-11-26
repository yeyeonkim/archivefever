package archivefever.server.swap;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface BookSwapRepository extends JpaRepository<BookSwap, Long> {

	boolean existsByOpenKey(String openKey);

	/** Every swap the user asked for or was asked; pass the user's id twice. */
	List<BookSwap> findByRequesterIdOrAddresseeId(String requesterId, String addresseeId);

	@Query("select s from BookSwap s where (s.requesterId = :user and s.addresseeId = :other)"
			+ " or (s.requesterId = :other and s.addresseeId = :user)")
	List<BookSwap> findBetween(@Param("user") String userId, @Param("other") String otherId);

}
