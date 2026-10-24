
package vn.utetra.repository;

import vn.utetra.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

	Optional<User> findByUsername(String username);

	Optional<User> findByEmail(String email);

	Optional<User> findByUsernameOrEmail(String username, String email);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	Optional<User> findByPhone(String phone);

	boolean existsByPhone(String phone);

	boolean existsByPhoneAndIdNot(String phone, Integer id);

	static String normalizePhone(String raw) {
		if (raw == null) return "";
		String p = raw.trim().replaceAll("[\\s.()-]", "");
		if (p.matches("\\+?84\\d{9}")) p = "0" + p.substring(p.length() - 9);
		return p;
	}

	default Optional<User> findByLogin(String login) {
		if (login == null || login.isBlank()) return Optional.empty();
		String l = login.trim();
		Optional<User> u = findByUsernameOrEmail(l, l);
		if (u.isEmpty() && l.contains("@")) u = findByEmail(l.toLowerCase());
		if (u.isPresent()) return u;
		String phone = normalizePhone(l);
		return phone.matches("0\\d{9}") ? findByPhone(phone) : Optional.empty();
	}

	List<User> findByRole_Name(String roleName);

	@Query("SELECT u FROM User u WHERE u.role.name = 'SHIPPER' AND u.branch.id = :branchId ORDER BY u.fullName")
	List<User> findShippersOfBranch(@Param("branchId") Integer branchId);

	@Query("""
			    SELECT u FROM User u
			    JOIN FETCH u.role
			    LEFT JOIN FETCH u.carrier
			    ORDER BY u.createdAt DESC
			""")
	List<User> findAllForAdmin();

	@Query("""
			    SELECT COUNT(u) FROM User u
			    WHERE u.role.name = 'ADMIN'
			      AND u.isActive = true
			""")
	long countActiveAdmins();

	@Query("""
			    SELECT u FROM User u
			    JOIN FETCH u.role
			    LEFT JOIN FETCH u.carrier
			    WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
			       OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			       OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
			       OR LOWER(COALESCE(u.phone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
			    ORDER BY u.createdAt DESC
			""")
	List<User> searchForAdmin(@Param("keyword") String keyword);
}
