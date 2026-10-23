package vn.utetra.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.response.HighlightPageRes;
import vn.utetra.dto.response.ProductCardRes;
import vn.utetra.entity.Product;
import vn.utetra.repository.ProductRepository;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Các danh sách món nổi bật trên trang Thực đơn (theo đề: 20 sản phẩm mới, bán chạy, đánh giá, yêu thích nhất)
 * + đổi Product sang thẻ món (kèm điểm đánh giá, số lượt thích).
 */
@Service
@Transactional(readOnly = true)
public class ProductHighlightService {

	private final ProductRepository productRepository;

	public ProductHighlightService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	/** Đề: "20 (sản phẩm mới, bán chạy, đánh giá, yêu thích) nhất" */
	public static final int TOP_SIZE = 20;

	/** Các loại khối hợp lệ: mới nhất, bán chạy, đánh giá cao, yêu thích nhất */
	public static final List<String> TYPES = List.of("newest", "sold", "rating", "favorite");

	/** Top limit món theo loại khối (loại sai -> mới nhất) */
	public List<ProductCardRes> top(String type, int limit) {
		return switch (normalizeType(type)) {
		case "sold" -> bestSellers(limit);
		case "rating" -> topRated(limit);
		case "favorite" -> mostFavorite(limit);
		default -> newest(limit);
		};
	}

	/**
	 * 1 trang (size món) trong 20 món nhất của loại khối.
	 * Vd page(sold, 1, 8) = món bán chạy hạng 9..16. Trang vượt quá -> trang cuối.
	 */
	public HighlightPageRes page(String type, int page, int size) {
		String t = normalizeType(type);
		List<ProductCardRes> all = top(t, TOP_SIZE);
		int total = all.size();
		int totalPages = Math.max(1, (total + size - 1) / size);
		int p = Math.min(Math.max(page, 0), totalPages - 1);
		int from = p * size;
		int to = Math.min(total, from + size);
		return new HighlightPageRes(t, total == 0 ? List.of() : all.subList(from, to), p, totalPages, total, from);
	}

	public static String normalizeType(String type) {
		return type != null && TYPES.contains(type) ? type : "newest";
	}

	/** Bán chạy nhất (theo số ly đã bán) */
	public List<ProductCardRes> bestSellers(int limit) {
		return toCards(available(productRepository.filterProducts(null, null, "sold")).stream().limit(limit).toList());
	}

	/** Mới nhất (theo ngày thêm món) */
	public List<ProductCardRes> newest(int limit) {
		return toCards(available(productRepository.filterProducts(null, null, "newest")).stream().limit(limit).toList());
	}

	/** Đánh giá cao nhất: điểm trung bình cao trước, bằng điểm thì nhiều lượt đánh giá trước */
	public List<ProductCardRes> topRated(int limit) {
		List<ProductCardRes> all = toCards(available(productRepository.findAll()));
		return all.stream().filter(c -> c.reviewCount() > 0)
				.sorted(Comparator.comparingDouble(ProductCardRes::avgRating).reversed()
						.thenComparing(Comparator.comparingLong(ProductCardRes::reviewCount).reversed()))
				.limit(limit).toList();
	}

	/** Yêu thích nhất: nhiều lượt thích trước */
	public List<ProductCardRes> mostFavorite(int limit) {
		List<ProductCardRes> all = toCards(available(productRepository.findAll()));
		return all.stream().filter(c -> c.favoriteCount() > 0)
				.sorted(Comparator.comparingLong(ProductCardRes::favoriteCount).reversed()
						.thenComparing(Comparator.comparingInt(ProductCardRes::soldCount).reversed()))
				.limit(limit).toList();
	}

	/** Đổi danh sách món sang thẻ món, giữ nguyên thứ tự */
	public List<ProductCardRes> toCards(List<Product> products) {
		if (products == null || products.isEmpty()) return Collections.emptyList();

		Map<Integer, Object[]> ratings = productRepository.ratingStats().stream()
				.collect(Collectors.toMap(r -> ((Number) r[0]).intValue(), Function.identity(), (a, b) -> a, HashMap::new));
		Map<Integer, Long> favorites = new LinkedHashMap<>();
		for (Object[] f : productRepository.favoriteStats()) {
			favorites.put(((Number) f[0]).intValue(), ((Number) f[1]).longValue());
		}

		return products.stream().filter(Objects::nonNull).map(p -> {
			Object[] r = ratings.get(p.getId());
			double avg = r == null || r[1] == null ? 0 : ((Number) r[1]).doubleValue();
			long reviews = r == null ? 0 : ((Number) r[2]).longValue();
			return new ProductCardRes(p.getId(), p.getName(),
					p.getCategory() != null ? p.getCategory().getName() : "",
					p.getBasePrice(), p.getImageCloudinaryId(), p.getDescription(),
					p.getSoldCount() != null ? p.getSoldCount() : 0,
					Math.round(avg * 10) / 10.0, reviews, favorites.getOrDefault(p.getId(), 0L),
					Boolean.TRUE.equals(p.getIsAvailable()));
		}).toList();
	}

	private static List<Product> available(List<Product> products) {
		if (products == null) return Collections.emptyList();
		// Bỏ topping mua riêng khỏi các khối nổi bật (mới / bán chạy / đánh giá / yêu thích): chỉ xếp hạng đồ uống
		return products.stream().filter(p -> p != null && !Boolean.FALSE.equals(p.getIsAvailable()) && !p.isToppingItem())
				.toList();
	}
}
