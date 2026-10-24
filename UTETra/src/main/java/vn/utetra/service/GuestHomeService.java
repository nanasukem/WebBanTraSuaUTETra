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

	public List<Product> getBestSellerProducts() {
		return productRepository.findBySoldCountGreaterThanOrderBySoldCountDesc(10);
	}

	public List<Product> getProducts(Integer categoryId) {
		if (categoryId != null && categoryId > 0) {
			return productRepository.findByCategoryId(categoryId);
		}
		return productRepository.findAll();
	}

	public List<Category> getAllCategories() {
		return categoryRepository.findAll();
	}

	public Product getProductDetail(Integer id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Sản phẩm không tồn tại!"));
	}
}