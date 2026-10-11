package vn.utetra.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.ProductRequest;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;

import java.io.IOException;
import java.util.List;

@Service
public class AdminProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final BranchRepository branchRepository;
	private final CloudinaryService cloudinaryService;

	public AdminProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
			BranchRepository branchRepository, CloudinaryService cloudinaryService) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.branchRepository = branchRepository;
		this.cloudinaryService = cloudinaryService;
	}

	public List<Product> getAllProducts() {
		return productRepository.findAll();
	}

	public Product getProductById(Integer id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + id));
	}

	@Transactional
	public Product createProduct(ProductRequest request) throws IOException {
		Product product = new Product();
		product.setName(request.getName());
		product.setBasePrice(request.getBasePrice());
		product.setDescription(request.getDescription());
		product.setIsAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true);
		product.setSoldCount(0);

		Category category = categoryRepository.findById(request.getCategoryId())
				.orElseThrow(() -> new RuntimeException("Danh mục không tồn tại!"));
		product.setCategory(category);

		Branch branch = branchRepository.findById(request.getBranchId())
				.orElseThrow(() -> new RuntimeException("Chi nhánh/Shop không tồn tại!"));
		product.setBranch(branch);

		// Upload ảnh lên Cloudinary nếu người dùng có chọn file
		if (request.getImageFile() != null && !request.getImageFile().isEmpty()) {
			String imageUrl = cloudinaryService.uploadImage(request.getImageFile(), "products");
			product.setImageCloudinaryId(imageUrl);
		}

		return productRepository.save(product);
	}

	@Transactional
	public Product updateProduct(Integer id, ProductRequest request) throws IOException {
		Product product = getProductById(id);
		product.setName(request.getName());
		product.setBasePrice(request.getBasePrice());
		product.setDescription(request.getDescription());
		if (request.getIsAvailable() != null) {
			product.setIsAvailable(request.getIsAvailable());
		}

		Category category = categoryRepository.findById(request.getCategoryId())
				.orElseThrow(() -> new RuntimeException("Danh mục không tồn tại!"));
		product.setCategory(category);

		// Nâng cấp ảnh mới trên Cloudinary nếu có upload lại
		if (request.getImageFile() != null && !request.getImageFile().isEmpty()) {
			// Xóa ảnh cũ
			if (product.getImageCloudinaryId() != null) {
				cloudinaryService.deleteImage(product.getImageCloudinaryId());
			}
			// Upload ảnh mới
			String imageUrl = cloudinaryService.uploadImage(request.getImageFile(), "products");
			product.setImageCloudinaryId(imageUrl);
		}

		return productRepository.save(product);
	}

	@Transactional
	public void deleteProduct(Integer id) {
		Product product = getProductById(id);
		// Xóa ảnh trên Cloudinary
		if (product.getImageCloudinaryId() != null) {
			cloudinaryService.deleteImage(product.getImageCloudinaryId());
		}
		productRepository.delete(product);
	}
}