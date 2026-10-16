package vn.utetra.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.utetra.dto.ProductFormDto;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;

import java.util.List;

@Service
public class AdminProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final CloudinaryService cloudinaryService;

	public AdminProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
			CloudinaryService cloudinaryService) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.cloudinaryService = cloudinaryService;
	}

	public List<Product> getAllProducts() {
		return productRepository.findAll();
	}

	public Product getProductById(Integer id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Không tìm thấy món ăn ID: " + id));
	}

	@Transactional
	public Product saveProduct(ProductFormDto form) {
		Category category = categoryRepository.findById(form.getCategoryId())
				.orElseThrow(() -> new IllegalArgumentException("Danh mục không tồn tại"));

		Product product;
		if (form.getId() != null) {
			product = getProductById(form.getId());
		} else {
			product = new Product();
		}

		product.setName(form.getName());
		product.setCategory(category);
		product.setBasePrice(form.getBasePrice());
		product.setDescription(form.getDescription());

		MultipartFile imageFile = form.getImageFile();
		if (imageFile != null && !imageFile.isEmpty()) {
			if (product.getImageCloudinaryId() != null) {
				cloudinaryService.deleteImage(product.getImageCloudinaryId());
			}
			String imageUrl = cloudinaryService.uploadImage(imageFile, "products");
			product.setImageCloudinaryId(imageUrl);
		}

		return productRepository.save(product);
	}

	@Transactional
	public void deleteProduct(Integer id) {
		Product product = getProductById(id);
		if (product.getImageCloudinaryId() != null) {
			cloudinaryService.deleteImage(product.getImageCloudinaryId());
		}
		productRepository.deleteById(id);
	}

	public List<Category> getAllCategories() {
		return categoryRepository.findAll();
	}

	@Transactional
	public Category saveCategory(String name, MultipartFile imageFile) {
		Category category = new Category();
		category.setName(name);

		if (imageFile != null && !imageFile.isEmpty()) {
			String imageUrl = cloudinaryService.uploadImage(imageFile, "categories");
			category.setImageCloudinaryId(imageUrl);
		}

		return categoryRepository.save(category);
	}
}