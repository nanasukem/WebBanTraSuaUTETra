package vn.utetra.service;

import org.springframework.stereotype.Service;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;

import java.util.List;

@Service
public class ProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;

	public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
	}

	public List<Category> getAllCategories() {
		return categoryRepository.findAll();
	}

	public List<Product> filterProducts(String keyword, Integer categoryId, String sortBy) {
		String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
		return productRepository.filterProducts(searchKeyword, categoryId, sortBy);
	}
}