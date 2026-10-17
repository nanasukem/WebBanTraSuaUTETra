package vn.utetra.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.CategoryFormDto;
import vn.utetra.dto.ProductDto;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.entity.Topping;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.ToppingRepository;
import vn.utetra.service.AdminProductService;
import vn.utetra.service.CloudinaryService;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminProductServiceImpl implements AdminProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final ToppingRepository toppingRepository;
	private final CloudinaryService cloudinaryService;

	// =========================================================================
	// 1. QUẢN LÝ SẢN PHẨM (ADMIN)
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<Product> getAllProducts() {
		return productRepository.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Product getProductById(Integer id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));
	}

	@Override
	public Product createProduct(ProductDto dto) {
		Product product = new Product();
		product.setName(dto.getName());
		product.setDescription(dto.getDescription());
		product.setBasePrice(dto.getBasePrice());
		product.setIsAvailable(dto.getIsAvailable() != null ? dto.getIsAvailable() : true);

		if (dto.getCategoryId() != null) {
			Category category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
			product.setCategory(category);
		}

		// Upload ảnh vào folder 'products' trên Cloudinary
		if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {
			String imageUrl = cloudinaryService.uploadImage(dto.getImageFile(), "products");
			product.setImageCloudinaryId(imageUrl);
		}

		return productRepository.save(product);
	}

	@Override
	public Product updateProduct(Integer id, ProductDto dto) {
		Product product = getProductById(id);
		product.setName(dto.getName());
		product.setDescription(dto.getDescription());
		product.setBasePrice(dto.getBasePrice());

		if (dto.getIsAvailable() != null) {
			product.setIsAvailable(dto.getIsAvailable());
		}

		if (dto.getCategoryId() != null) {
			Category category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
			product.setCategory(category);
		}

		// Nếu chọn ảnh mới: xóa ảnh cũ rồi tải ảnh mới lên
		if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {
			if (product.getImageCloudinaryId() != null && !product.getImageCloudinaryId().isBlank()) {
				cloudinaryService.deleteImage(product.getImageCloudinaryId());
			}
			String newImageUrl = cloudinaryService.uploadImage(dto.getImageFile(), "products");
			product.setImageCloudinaryId(newImageUrl);
		}

		return productRepository.save(product);
	}

	@Override
	public void deleteProduct(Integer id) {
		Product product = getProductById(id);
		if (product.getImageCloudinaryId() != null && !product.getImageCloudinaryId().isBlank()) {
			cloudinaryService.deleteImage(product.getImageCloudinaryId());
		}
		productRepository.delete(product);
	}

	// =========================================================================
	// 2. QUẢN LÝ DANH MỤC (ADMIN & MANAGER)
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<Category> getAllCategories() {
		return categoryRepository.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Category getCategoryById(Integer id) {
		return categoryRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + id));
	}

	@Override
	public Category saveCategory(CategoryFormDto dto) {
		Category category;
		if (dto.getId() != null) {
			category = getCategoryById(dto.getId());
		} else {
			category = new Category();
		}

		category.setName(dto.getName());

		// Upload ảnh danh mục vào folder 'categories' trên Cloudinary
		if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {
			if (category.getImageCloudinaryId() != null && !category.getImageCloudinaryId().isBlank()) {
				cloudinaryService.deleteImage(category.getImageCloudinaryId());
			}
			String imageUrl = cloudinaryService.uploadImage(dto.getImageFile(), "categories");
			category.setImageCloudinaryId(imageUrl);
		}

		return categoryRepository.save(category);
	}

	@Override
	public void deleteCategory(Integer id) {
		Category category = getCategoryById(id);
		if (category.getImageCloudinaryId() != null && !category.getImageCloudinaryId().isBlank()) {
			cloudinaryService.deleteImage(category.getImageCloudinaryId());
		}
		categoryRepository.delete(category);
	}

	// =========================================================================
	// 3. QUẢN LÝ TOPPING (ADMIN)
	// =========================================================================

	@Override
	@Transactional(readOnly = true)
	public List<Topping> getAllToppings() {
		return toppingRepository.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Topping getToppingById(Integer id) {
		return toppingRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Không tìm thấy topping với ID: " + id));
	}

	@Override
	public Topping saveTopping(Topping topping) {
		return toppingRepository.save(topping);
	}

	@Override
	public void deleteTopping(Integer id) {
		toppingRepository.deleteById(id);
	}
}