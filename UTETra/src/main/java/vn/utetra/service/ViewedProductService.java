package vn.utetra.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.dto.response.ProductCardRes;
import vn.utetra.entity.Product;
import vn.utetra.entity.ViewedProduct;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.UserRepository;
import vn.utetra.repository.ViewedProductRepository;

/**
 * Sản phẩm đã xem: mở trang chi tiết món thì ghi lại.
 * Xem lại món cũ -> chỉ cập nhật thời gian (bảng ViewedProducts có UNIQUE user_id + product_id),
 * nên danh sách luôn xếp "xem gần nhất lên đầu", không bị trùng món.
 */
@Service
@Transactional(readOnly = true)
public class ViewedProductService {

	/** Trang "Đã xem" hiện tối đa 40 món gần nhất */
	public static final int MAX_ITEMS = 40;

	private final ViewedProductRepository viewedProductRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final ProductHighlightService productHighlightService;

	public ViewedProductService(ViewedProductRepository viewedProductRepository, ProductRepository productRepository,
			UserRepository userRepository, ProductHighlightService productHighlightService) {
		this.viewedProductRepository = viewedProductRepository;
		this.productRepository = productRepository;
		this.userRepository = userRepository;
		this.productHighlightService = productHighlightService;
	}

	/** Ghi nhận user vừa xem món (gọi khi mở trang chi tiết). Món không tồn tại thì bỏ qua */
	@Transactional
	public void record(Integer userId, Integer productId) {
		if (userId == null || productId == null) {
			return;
		}
		ViewedProduct v = viewedProductRepository.findByUserIdAndProductId(userId, productId).orElse(null);
		if (v == null) {
			if (!productRepository.existsById(productId)) {
				return;
			}
			v = new ViewedProduct();
			v.setUser(userRepository.getReferenceById(userId));
			v.setProduct(productRepository.getReferenceById(productId));
		}
		v.setViewedAt(LocalDateTime.now());
		viewedProductRepository.saveAndFlush(v);
	}

	/** limit món xem gần nhất */
	public List<ProductCardRes> recent(Integer userId, int limit) {
		List<Product> products = viewedProductRepository
				.findByUserIdOrderByViewedAtDescIdDesc(userId, PageRequest.of(0, Math.max(1, limit))).stream()
				.map(ViewedProduct::getProduct).toList();
		return productHighlightService.toCards(products);
	}

	public long count(Integer userId) {
		return viewedProductRepository.countByUserId(userId);
	}

	/** Xoá lịch sử xem. Trả về số món đã xoá */
	@Transactional
	public int clear(Integer userId) {
		return viewedProductRepository.deleteAllOfUser(userId);
	}
}
