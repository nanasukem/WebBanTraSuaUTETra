package vn.utetra.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	public List<Category> getAllCategories() {
		return categoryRepository.findAll();
	}

	public List<Product> filterProducts(String keyword, Integer categoryId, BigDecimal minPrice, BigDecimal maxPrice) {
		return productRepository.filterProducts(keyword, categoryId, minPrice, maxPrice);
	}

	public Product getProductById(Integer id) {
		return productRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm!"));
	}
}