package vn.utetra.service;

import org.springframework.stereotype.Service;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;

import java.util.List;

@Service
public class GuestHomeService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;

	public GuestHomeService(ProductRepository productRepository, CategoryRepository categoryRepository) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
	}

	/**
	 * Lấy danh sách sản phẩm bán chạy (> 10 lượt bán) cho Banner/Section nổi bật
	 */
	public List<Product> getBestSellerProducts() {
		return productRepository.findBySoldCountGreaterThanOrderBySoldCountDesc(10);
	}

	/** Lấy toàn bộ sản phẩm hoặc lọc theo danh mục */
	public List<Product> getProducts(Integer categoryId) {
		if (categoryId != null && categoryId > 0) {
			return productRepository.findByCategoryId(categoryId);
		}
		return productRepository.findAll();
	}

	/** Lấy toàn bộ danh mục để hiển thị thanh lọc */
	public List<Category> getAllCategories() {
		return categoryRepository.findAll();
	}

	/** Lấy chi tiết 1 sản phẩm */
	public Product getProductDetail(Integer id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại!"));
	}
}