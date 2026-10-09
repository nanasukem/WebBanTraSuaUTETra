package vn.utetra.repository;

import vn.utetra.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

	Optional<User> findByUsername(String username);

	Optional<User> findByEmail(String email);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	/** Lấy user theo tên role, ví dụ "VENDOR" */
	List<User> findByRole_Name(String roleName);
}