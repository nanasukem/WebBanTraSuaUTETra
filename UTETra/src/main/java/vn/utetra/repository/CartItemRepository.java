package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
	List<CartItem> findByUserId(Integer userId);

	/** Giữ lại cho code cũ. Lưu ý: văng lỗi nếu có nhiều dòng khác topping */
	Optional<CartItem> findByUserIdAndProductIdAndSizeAndSugarLevelAndIceLevel(Integer userId, Integer productId,
			String size, Integer sugarLevel, Integer iceLevel);

	/** Các dòng cùng món/size/đường/đá (có thể khác topping) - dùng để cộng dồn */
	List<CartItem> findAllByUserIdAndProductIdAndSizeAndSugarLevelAndIceLevel(Integer userId, Integer productId,
			String size, Integer sugarLevel, Integer iceLevel);

	void deleteByUserId(Integer userId);
}