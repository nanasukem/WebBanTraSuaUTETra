package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.ProductSize;

import java.util.List;
import java.util.Optional;

public interface ProductSizeRepository extends JpaRepository<ProductSize, Integer> {

	Optional<ProductSize> findByProductIdAndSize(Integer productId, String size);

	List<ProductSize> findByProductId(Integer productId);
}