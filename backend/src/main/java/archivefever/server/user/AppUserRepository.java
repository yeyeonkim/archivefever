package archivefever.server.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppUserRepository extends JpaRepository<AppUser, String> {

	Optional<AppUser> findByHandle(String handle);

	boolean existsByHandle(String handle);

	List<AppUser> findByHandleIsNull();

	/** Users whose name or handle contains the text; "%" and "_" in it are taken literally. */
	List<AppUser> findByNicknameContainingIgnoreCaseOrHandleContainingIgnoreCaseOrderByHandleAsc(String nickname,
			String handle, Pageable page);

	List<AppUser> findByHandleContainingIgnoreCaseOrderByHandleAsc(String handle, Pageable page);

}
