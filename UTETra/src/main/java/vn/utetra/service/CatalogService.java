package vn.utetra.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.dto.response.CategoryCardRes;
import vn.utetra.dto.response.MenuSectionRes;
import vn.utetra.dto.response.ProductCardRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class CatalogService {

	private static final Logger logger = LoggerFactory.getLogger(CatalogService.class);

	public static final List<String> CATEGORY_SORTS = List.of("newest", "sold", "price_asc", "price_desc");
	public static final List<String> MENU_SORTS = List.of("default", "newest", "sold", "price_asc", "price_desc");

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final BranchRepository branchRepository;
	private final ProductHighlightService productHighlightService;
	private final BranchReviewManagementService branchReviewManagementService;

	public CatalogService(ProductRepository productRepository, CategoryRepository categoryRepository,
			BranchRepository branchRepository, ProductHighlightService productHighlightService,
			BranchReviewManagementService branchReviewManagementService) {
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.branchRepository = branchRepository;
		this.productHighlightService = productHighlightService;
		this.branchReviewManagementService = branchReviewManagementService;
	}

	public record CardPage(List<ProductCardRes> cards, int page, int totalPages, long totalItems) {
	}

	public record BranchOverview(List<Branch> branches, Map<Integer, Map<String, Object>> reviewSummaries) {
	}

	public CardPage bestSellers(int minSold, int page, int size) {
		page = Math.max(page, 0);
		Page<Product> p = productRepository.findBySoldCountGreaterThanAndIsAvailableTrueOrderBySoldCountDescIdAsc(minSold,
				PageRequest.of(page, size));
		if (p.getTotalPages() > 0 && page >= p.getTotalPages()) {
			page = p.getTotalPages() - 1;
			p = productRepository.findBySoldCountGreaterThanAndIsAvailableTrueOrderBySoldCountDescIdAsc(minSold,
					PageRequest.of(page, size));
		}
		return new CardPage(productHighlightService.toCards(p.getContent()), page, p.getTotalPages(), p.getTotalElements());
	}

	public BranchOverview branchOverview() {
		List<Branch> branches = branchRepository.findAll();
		Map<Integer, Map<String, Object>> summaries = new HashMap<>();
		for (Branch b : branches) {
			summaries.put(b.getId(), branchReviewManagementService.getBranchReviewSummary(b.getId()));
		}
		return new BranchOverview(branches, summaries);
	}

	public Optional<Category> findCategory(Integer id) {
		return id == null ? Optional.empty() : categoryRepository.findById(id);
	}

	public CardPage categoryProducts(Integer categoryId, String sortBy, int page, int size) {
		page = Math.max(page, 0);
		Page<Product> p = productRepository.menuProductsPaged(categoryId, sortBy, PageRequest.of(page, size));
		if (p.getTotalPages() > 0 && page >= p.getTotalPages()) {
			page = p.getTotalPages() - 1;
			p = productRepository.menuProductsPaged(categoryId, sortBy, PageRequest.of(page, size));
		}
		return new CardPage(productHighlightService.toCards(p.getContent()), page, p.getTotalPages(), p.getTotalElements());
	}

	public List<CategoryCardRes> categoryCards() {
		Map<Integer, Long> counts = new HashMap<>();
		try {
			for (Object[] row : productRepository.countAvailableByCategory()) {
				if (row[0] != null) {
					counts.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
				}
			}
		} catch (Exception e) {
			logger.error("Không đếm được số món theo danh mục", e);
		}
		return categoryRepository.findAll().stream().map(c -> new CategoryCardRes(c.getId(), c.getName(),
				c.getImageCloudinaryId(), counts.getOrDefault(c.getId(), 0L))).toList();
	}

	public List<MenuSectionRes> menuSections(String keyword, String sortBy) {
		List<Product> products = productRepository.menuProducts(keyword == null || keyword.isEmpty() ? null : keyword,
				null, "default".equals(sortBy) ? "id" : sortBy);
		if ("default".equals(sortBy)) {
			products = products.stream()
					.sorted(Comparator.comparing((Product p) -> !Boolean.TRUE.equals(p.getIsAvailable()))
							.thenComparing(Product::getId))
					.toList();
		}
		List<ProductCardRes> cards = productHighlightService.toCards(products);

		Map<Integer, List<ProductCardRes>> byCategory = new LinkedHashMap<>();
		for (int i = 0; i < products.size() && i < cards.size(); i++) {
			Category c = products.get(i).getCategory();
			byCategory.computeIfAbsent(c != null ? c.getId() : 0, k -> new ArrayList<>()).add(cards.get(i));
		}
		List<MenuSectionRes> sections = new ArrayList<>();
		for (Category c : categoryRepository.findAll()) {
			List<ProductCardRes> items = byCategory.remove(c.getId());
			if (items != null && !items.isEmpty()) {
				sections.add(new MenuSectionRes(c.getId(), c.getName(), items));
			}
		}
		byCategory.values().stream().filter(l -> !l.isEmpty()).findFirst()
				.ifPresent(rest -> sections.add(new MenuSectionRes(0, "Khác", rest)));
		return sections;
	}
}
