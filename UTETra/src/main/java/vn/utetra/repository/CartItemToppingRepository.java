package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.CartItemTopping;

import java.util.List;

public interface CartItemToppingRepository extends JpaRepository<CartItemTopping, Integer> {

	List<CartItemTopping> findByCartItemId(Integer cartItemId);

	void deleteByCartItemId(Integer cartItemId);
}