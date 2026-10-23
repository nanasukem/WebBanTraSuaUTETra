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
import vn.utetra.service.AdminProductService;
import vn.utetra.service.CloudinaryService;
import vn.utetra.service.ToppingService;

import java.io.IOException;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AdminProductServiceImpl implements AdminProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final ToppingService toppingService;
	private final CloudinaryService cloudinaryService;

	// =========================================================
	// 1. QUAN LY SAN PHAM
	// =========================================================

	@Override
	@Transactional(readOnly = true)
	public List<Product> getAllProducts() {
		return productRepository.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Product getProductById(Integer id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Khong tim thay san pham co ID: " + id));
	}

	/**
	 * Tim danh muc hop le. Product.category bat buoc khong duoc null.
	 */
	private Category getRequiredCategory(Integer categoryId) {
		if (categoryId == null) {
			throw new IllegalArgumentException("Vui long chon danh muc san pham.");
		}

		return categoryRepository.findById(categoryId)
				.orElseThrow(() -> new IllegalArgumentException("Danh muc khong ton tai."));
	}

	@Override
	public Product createProduct(ProductDto dto) throws IOException {
		if (dto == null) {
			throw new IllegalArgumentException("Thong tin san pham khong hop le.");
		}
		if (toppingService.isToppingCategory(dto.getCategoryId())) {
			throw new IllegalArgumentException(
					"Danh mục Topping do trang Quản lý topping tự quản lý, hãy thêm topping ở trang đó.");
		}

		Product product = new Product();

		product.setName(dto.getName());
		product.setDescription(dto.getDescription());
		product.setBasePrice(dto.getBasePrice());

		// Mac dinh san pham duoc phep ban tren toan chuoi
		product.setIsAvailable(dto.getIsAvailable() != null ? dto.getIsAvailable() : true);

		// Danh muc bat buoc phai ton tai
		product.setCategory(getRequiredCategory(dto.getCategoryId()));

		// Tai anh len Cloudinary neu co chon anh
		if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {

			String imageUrl = cloudinaryService.uploadImage(dto.getImageFile(), "products");

			product.setImageCloudinaryId(imageUrl);
		}

		// Gia tri mac dinh cua soldCount va createdAt duoc entity xu ly
		return productRepository.save(product);
	}

	@Override
	public Product updateProduct(Integer id, ProductDto dto) throws IOException {

		if (dto == null) {
			throw new IllegalArgumentException("Thong tin san pham khong hop le.");
		}

		Product product = getProductById(id);
		if (product.getTopping() != null) {
			throw new IllegalArgumentException("\"" + product.getName()
					+ "\" là topping bán riêng, hãy sửa / bật tắt ở trang Quản lý topping.");
		}
		if (toppingService.isToppingCategory(dto.getCategoryId())) {
			throw new IllegalArgumentException(
					"Danh mục Topping do trang Quản lý topping tự quản lý, hãy thêm topping ở trang đó.");
		}

		product.setName(dto.getName());
		product.setDescription(dto.getDescription());
		product.setBasePrice(dto.getBasePrice());

		// Chi cap nhat trang thai neu form co gui gia tri
		if (dto.getIsAvailable() != null) {
			product.setIsAvailable(dto.getIsAvailable());
		}

		// Neu form gui danh muc moi thi phai kiem tra danh muc
		if (dto.getCategoryId() != null) {
			product.setCategory(getRequiredCategory(dto.getCategoryId()));
		}

		// Thay anh neu nguoi dung chon anh moi
		if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {

			String oldImage = product.getImageCloudinaryId();

			String newImage = cloudinaryService.uploadImage(dto.getImageFile(), "products");

			product.setImageCloudinaryId(newImage);

			// Xoa anh cu sau khi upload anh moi thanh cong
			if (oldImage != null && !oldImage.isBlank()) {
				try {
					cloudinaryService.deleteImage(oldImage);
				} catch (Exception e) {
					// Khong huy cap nhat san pham chi vi xoa anh cu that bai
					System.err.println("Khong the xoa anh Cloudinary cu: " + e.getMessage());
				}
			}
		}

		return productRepository.save(product);
	}

	@Override
	public void deleteProduct(Integer id) {
		Product product = getProductById(id);
		if (product.getTopping() != null) {
			throw new IllegalArgumentException("\"" + product.getName()
					+ "\" là topping bán riêng, hãy sửa / bật tắt ở trang Quản lý topping.");
		}

		// Xoa san pham truoc hay anh truoc can phu thuoc rang buoc DB.
		// O day xoa anh truoc, giu logic tuong thich voi code hien tai.
		String imageUrl = product.getImageCloudinaryId();

		productRepository.delete(product);

		if (imageUrl != null && !imageUrl.isBlank()) {
			try {
				cloudinaryService.deleteImage(imageUrl);
			} catch (Exception e) {
				System.err.println("Khong the xoa anh san pham tren Cloudinary: " + e.getMessage());
			}
		}
	}

	// =========================================================
	// 2. QUAN LY DANH MUC
	// =========================================================

	@Override
	@Transactional(readOnly = true)
	public List<Category> getAllCategories() {
		return categoryRepository.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Category getCategoryById(Integer id) {
		return categoryRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Khong tim thay danh muc co ID: " + id));
	}

	@Override
	public Category saveCategory(CategoryFormDto dto) throws IOException {

		if (dto == null) {
			throw new IllegalArgumentException("Thong tin danh muc khong hop le.");
		}

		if (dto.getName() == null || dto.getName().isBlank()) {
			throw new IllegalArgumentException("Ten danh muc khong duoc de trong.");
		}

		String categoryName = dto.getName().trim();

		if (categoryName.length() > 100) {
			throw new IllegalArgumentException("Ten danh muc khong duoc vuot qua 100 ky tu.");
		}

		Category category;

		if (dto.getId() != null) {
			// Cap nhat danh muc hien co
			category = getCategoryById(dto.getId());
		} else {
			// Tao danh muc moi
			category = new Category();
		}

		category.setName(categoryName);

		// Chi upload khi nguoi dung chon anh moi
		if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {

			String oldImage = category.getImageCloudinaryId();

			String newImage = cloudinaryService.uploadImage(dto.getImageFile(), "categories");

			category.setImageCloudinaryId(newImage);

			if (oldImage != null && !oldImage.isBlank()) {
				try {
					cloudinaryService.deleteImage(oldImage);
				} catch (Exception e) {
					System.err.println("Khong the xoa anh danh muc cu: " + e.getMessage());
				}
			}
		}

		// Neu khong chon anh moi, giu nguyen anh cu khi cap nhat
		return categoryRepository.save(category);
	}

	@Override
	public void deleteCategory(Integer id) {
		Category category = getCategoryById(id);

		// Không cho xóa danh mục nếu vẫn có sản phẩm tham chiếu.
		if (!productRepository.findByCategoryId(id).isEmpty()) {
			throw new IllegalArgumentException(
					"Không thể xóa danh mục đang có sản phẩm. " + "Hãy chuyển sản phẩm sang danh mục khác trước.");
		}

		String imageUrl = category.getImageCloudinaryId();

		categoryRepository.delete(category);

		// Chỉ xóa ảnh sau khi yêu cầu xóa danh mục đã được thực hiện.
		if (imageUrl != null && !imageUrl.isBlank()) {
			try {
				cloudinaryService.deleteImage(imageUrl);
			} catch (Exception e) {
				System.err.println("Không thể xóa ảnh danh mục trên Cloudinary: " + e.getMessage());
			}
		}
	}
}