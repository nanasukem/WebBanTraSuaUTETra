package vn.utetra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.utetra.entity.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {

	Optional<Favorite> findByUserIdAndProductId(Integer userId, Integer productId);

	boolean existsByUserIdAndProductId(Integer userId, Integer productId);

	List<Favorite> findByUserIdOrderByCreatedAtDescIdDesc(Integer userId);

	long countByProductId(Integer productId);

	long countByUserId(Integer userId);
}
