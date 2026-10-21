package vn.utetra.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.dto.response.HighlightPageRes;
import vn.utetra.dto.response.ProductCardRes;
import vn.utetra.dto.response.ProductDetailRes;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.entity.ProductSize;
import vn.utetra.entity.Review;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.ProductSizeRepository;
import vn.utetra.repository.ReviewRepository;

/**
 * Sản phẩm phía khách:
 * - Lọc / tìm kiếm (giữ hàm cũ)
 * - 20 sản phẩm mới nhất / bán chạy nhất / đánh giá cao nhất / yêu thích nhất, có phân trang
 * - Chi tiết 1 món (giá theo size, điểm đánh giá, số sao từng mức) + món cùng danh mục
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

	private static final Map<String, Integer> SIZE_ORDER = Map.of("S", 0, "M", 1, "L", 2);

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final ProductHighlightService productHighlightService;
	private final ProductSizeRepository productSizeRepository;
	private final ReviewRepository reviewRepository;

	public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
			ProductHighlightService productHighlightService, ProductSizeRepository productSizeRepository,
			ReviewRepository reviewRepository) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.productHighlightService = productHighlightService;
		this.productSizeRepository = productSizeRepository;
		this.reviewRepository = reviewRepository;
	}

	public List<Category> getAllCategories() {
		return categoryRepository.findAll();
	}

	public List<Product> filterProducts(String keyword, Integer categoryId, String sortBy) {
		String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
		return productRepository.filterProducts(searchKeyword, categoryId, sortBy);
	}

	// ================= 20 SẢN PHẨM NHẤT =================

	/**
	 * 1 trang trong 20 món nhất. type: newest | sold | rating | favorite (sai -> newest).
	 * Vd topProducts("sold", 1, 8) = món bán chạy hạng 9..16.
	 */
	public HighlightPageRes topProducts(String type, int page, int size) {
		return productHighlightService.page(type, page, size <= 0 ? 8 : size);
	}

	/** Đủ 20 món nhất của 1 loại (không phân trang) */
	public List<ProductCardRes> top20(String type) {
		return productHighlightService.top(type, ProductHighlightService.TOP_SIZE);
	}

	// ================= CHI TIẾT MÓN =================

	/** Chi tiết 1 món (kể cả món đang tạm ngưng bán - trang hiện thông báo riêng) */
	public ProductDetailRes getDetail(Integer productId) {
		Product p = productRepository.findById(productId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món #" + productId));
		ProductCardRes card = productHighlightService.toCards(List.of(p)).get(0);

		BigDecimal base = p.getBasePrice() == null ? BigDecimal.ZERO : p.getBasePrice();
		List<ProductDetailRes.SizeOption> sizes = productSizeRepository.findByProductId(productId).stream()
				.sorted(Comparator.comparing((ProductSize s) -> SIZE_ORDER.getOrDefault(s.getSize(), 9)))
				.map(s -> new ProductDetailRes.SizeOption(s.getSize(),
						base.add(s.getExtraPrice() == null ? BigDecimal.ZERO : s.getExtraPrice())))
				.toList();

		List<Long> stars = new ArrayList<>(List.of(0L, 0L, 0L, 0L, 0L));
		for (Review r : reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId)) {
			if (r.getRating() != null && r.getRating() >= 1 && r.getRating() <= 5) {
				int i = 5 - r.getRating();
				stars.set(i, stars.get(i) + 1);
			}
		}

		return new ProductDetailRes(p.getId(), p.getName(), p.getCategory() != null ? p.getCategory().getId() : null,
				card.categoryName(), base, p.getImageCloudinaryId(), p.getDescription(),
				!Boolean.FALSE.equals(p.getIsAvailable()), card.soldCount(), card.avgRating(), card.reviewCount(),
				card.favoriteCount(), sizes, stars);
	}

	/** Món cùng danh mục (bán chạy trước), bỏ món đang xem */
	public List<ProductCardRes> related(Integer productId, int limit) {
		Product p = productRepository.findById(productId).orElse(null);
		if (p == null || p.getCategory() == null) {
			return List.of();
		}
		List<Product> same = productRepository.filterProducts(null, p.getCategory().getId(), "sold").stream()
				.filter(x -> !x.getId().equals(productId)).limit(limit).toList();
		return productHighlightService.toCards(same);
	}
}
