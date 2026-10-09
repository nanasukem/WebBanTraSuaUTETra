package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
	List<CartItem> findByUserId(Integer userId);

	Optional<CartItem> findByUserIdAndProductIdAndSizeAndSugarLevelAndIceLevel(Integer userId, Integer productId,
			String size, Integer sugarLevel, Integer iceLevel);

	void deleteByUserId(Integer userId);
}