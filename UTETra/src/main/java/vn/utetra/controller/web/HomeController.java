package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;

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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.dto.response.CategoryCardRes;
import vn.utetra.dto.response.MenuSectionRes;
import vn.utetra.dto.response.ProductCardRes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.entity.User;

import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.UserRepository;
import vn.utetra.service.BranchReviewManagementService;
import vn.utetra.service.OrderFlowService;
import vn.utetra.service.ProductHighlightService;
import vn.utetra.service.ViewedProductService;

@Controller
public class HomeController {

	private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

	private static final int PAGE_SIZE = 20;

	/** Đề - Guest: trang chủ hiện sản phẩm bán TRÊN 10 */
	private static final int GUEST_MIN_SOLD = 10;

	/**
	 * Trang chủ User: mỗi trang của khối "20 món nhất" hiện 8 món (20 món = 3
	 * trang)
	 */
	private static final int HIGHLIGHT_PAGE_SIZE = 8;

	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final ProductHighlightService productHighlightService;
	private final OrderFlowService orderFlowService;
	private final ViewedProductService viewedProductService;
	private final vn.utetra.service.ReviewService reviewService;
	private final BranchRepository branchRepository;
	private final BranchReviewManagementService branchReviewManagementService;

	public HomeController(UserRepository userRepository, ProductRepository productRepository,
			CategoryRepository categoryRepository, ProductHighlightService productHighlightService,
			OrderFlowService orderFlowService, ViewedProductService viewedProductService,
			vn.utetra.service.ReviewService reviewService, BranchRepository branchRepository,
			BranchReviewManagementService branchReviewManagementService) {
		this.reviewService = reviewService;
		this.userRepository = userRepository;
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
		this.productHighlightService = productHighlightService;
		this.orderFlowService = orderFlowService;
		this.viewedProductService = viewedProductService;
		this.branchRepository = branchRepository;
		this.branchReviewManagementService = branchReviewManagementService;
	}

	@GetMapping("/")
	public String index(@RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		boolean isLoggedIn = authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(authentication.getName());

		if (isLoggedIn) {

			Optional<User> userOptional = userRepository.findByUsername(authentication.getName());

			if (userOptional.isPresent()) {

				User user = userOptional.get();
				session.setAttribute("user", user);

				String roleName = "";

				if (user.getRole() != null && user.getRole().getName() != null) {

					roleName = user.getRole().getName().trim();
				}

				if ("SHIPPER".equalsIgnoreCase(roleName)) {
					return "redirect:/shipper/orders";
				}

				if ("USER".equalsIgnoreCase(roleName) || "MANAGER".equalsIgnoreCase(roleName)
						|| "ADMIN".equalsIgnoreCase(roleName)) {
					return userHome(user, roleName.toUpperCase(), model);
				}

			} else {
				session.removeAttribute("user");
			}

		} else {
			session.removeAttribute("user");
		}

		return guestHome(page, model);
	}

	@GetMapping("/login-success")
	public String loginSuccess(HttpSession session) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || "anonymousUser".equals(authentication.getName())) {
			return "redirect:/auth/login";
		}
		Optional<User> userOptional = userRepository.findByUsername(authentication.getName());
		if (userOptional.isEmpty()) {
			return "redirect:/";
		}
		User user = userOptional.get();
		session.setAttribute("user", user);
		String role = user.getRole() != null && user.getRole().getName() != null
				? user.getRole().getName().trim().toUpperCase()
				: "";
		return switch (role) {
		case "ADMIN" -> "redirect:/admin/dashboard";
		case "MANAGER" -> "redirect:/manager/dashboard";
		case "SHIPPER" -> "redirect:/shipper/orders";
		default -> "redirect:/";
		};
	}

	/** Trang chủ Guest: chỉ món bán trên 10 ly, nhiều nhất trước, phân trang */
	private String guestHome(int page, Model model) {
		page = Math.max(page, 0);
		Page<Product> productPage = productRepository
				.findBySoldCountGreaterThanAndIsAvailableTrueOrderBySoldCountDescIdAsc(GUEST_MIN_SOLD,
						PageRequest.of(page, PAGE_SIZE));

		if (productPage.getTotalPages() > 0 && page >= productPage.getTotalPages()) {
			page = productPage.getTotalPages() - 1;
			productPage = productRepository.findBySoldCountGreaterThanAndIsAvailableTrueOrderBySoldCountDescIdAsc(
					GUEST_MIN_SOLD, PageRequest.of(page, PAGE_SIZE));
		}

		// BỔ SUNG: Đưa danh sách chi nhánh và điểm đánh giá sao trung bình ra trang chủ
		// cho Guest
		List<Branch> branches = branchRepository.findAll();
		Map<Integer, Map<String, Object>> branchReviewSummaries = new HashMap<>();
		for (Branch b : branches) {
			branchReviewSummaries.put(b.getId(), branchReviewManagementService.getBranchReviewSummary(b.getId()));
		}
		model.addAttribute("branches", branches);
		model.addAttribute("branchReviewSummaries", branchReviewSummaries);

		model.addAttribute("products", productHighlightService.toCards(productPage.getContent()));
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", productPage.getTotalPages());
		model.addAttribute("totalItems", productPage.getTotalElements());
		model.addAttribute("rankOffset", page * PAGE_SIZE);
		model.addAttribute("minSold", GUEST_MIN_SOLD);
		model.addAttribute("isBuyer", false);
		model.addAttribute("isGuest", true);
		model.addAttribute("cardMode", "guest");
		return "home";
	}

	/**
	 * Trang chủ trưng bày: USER (có đơn hàng + đặt món) hoặc MANAGER/ADMIN (chỉ
	 * xem)
	 */
	private String userHome(User user, String role, Model model) {
		boolean isBuyer = "USER".equals(role);
		model.addAttribute("me", user);
		model.addAttribute("role", role);

		if (isBuyer) {
			model.addAttribute("orderTabs", OrderTab.values());
			try {
				model.addAttribute("orderCounts", orderFlowService.myOrderCounts(user.getId()));
			} catch (Exception e) {
				logger.error("Không đếm được đơn của user {}", user.getId(), e);
				model.addAttribute("orderCounts", Map.of());
			}
			model.addAttribute("reviewReminder", reviewService.pendingOrderCount(user.getId()));
			model.addAttribute("recentViewed", viewedProductService.recent(user.getId(), 8));
		}

		model.addAttribute("categories", categoryCards());

		// BỔ SUNG: Đưa danh sách chi nhánh và điểm đánh giá sao trung bình ra trang chủ
		// cho User
		List<Branch> branches = branchRepository.findAll();
		Map<Integer, Map<String, Object>> branchReviewSummaries = new HashMap<>();
		for (Branch b : branches) {
			branchReviewSummaries.put(b.getId(), branchReviewManagementService.getBranchReviewSummary(b.getId()));
		}
		model.addAttribute("branches", branches);
		model.addAttribute("branchReviewSummaries", branchReviewSummaries);

		model.addAttribute("newestPage", productHighlightService.page("newest", 0, HIGHLIGHT_PAGE_SIZE));
		model.addAttribute("soldPage", productHighlightService.page("sold", 0, HIGHLIGHT_PAGE_SIZE));
		model.addAttribute("ratingPage", productHighlightService.page("rating", 0, HIGHLIGHT_PAGE_SIZE));
		model.addAttribute("favoritePage", productHighlightService.page("favorite", 0, HIGHLIGHT_PAGE_SIZE));

		model.addAttribute("isBuyer", isBuyer);
		model.addAttribute("isGuest", false);
		model.addAttribute("cardMode", cardModeOf(role));
		return "user/home"; // Lưu ý: Nếu user dùng template riêng, bạn có thể bổ sung tương tự cho file
							// user/home.html nếu cần
	}

	@GetMapping("/menu/highlight/{type}")
	public String highlightPage(@PathVariable("type") String type,
			@RequestParam(name = "page", defaultValue = "0") int page, HttpSession session, Model model) {
		model.addAttribute("pg", productHighlightService.page(type, page, HIGHLIGHT_PAGE_SIZE));
		model.addAttribute("mode", cardModeOf(roleOf(session)));
		return "fragments/product-card :: hlBody";
	}

	@GetMapping("/category/{id}")
	public String category(@PathVariable("id") Integer id,
			@RequestParam(name = "sortBy", defaultValue = "sold") String sortBy,
			@RequestParam(name = "page", defaultValue = "0") int page, HttpSession session, Model model) {

		Optional<Category> categoryOpt = categoryRepository.findById(id);
		if (categoryOpt.isEmpty()) {
			return "redirect:/menu#thuc-don";
		}

		if (!List.of("newest", "sold", "price_asc", "price_desc").contains(sortBy)) {
			sortBy = "sold";
		}
		page = Math.max(page, 0);

		Page<Product> productPage = productRepository.filterProductsPaged(null, id, sortBy,
				PageRequest.of(page, PAGE_SIZE));
		if (productPage.getTotalPages() > 0 && page >= productPage.getTotalPages()) {
			page = productPage.getTotalPages() - 1;
			productPage = productRepository.filterProductsPaged(null, id, sortBy, PageRequest.of(page, PAGE_SIZE));
		}

		String role = roleOf(session);
		model.addAttribute("category", categoryOpt.get());
		model.addAttribute("categories", categoryCards());
		model.addAttribute("products", productHighlightService.toCards(productPage.getContent()));
		model.addAttribute("sortBy", sortBy);
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", productPage.getTotalPages());
		model.addAttribute("totalItems", productPage.getTotalElements());
		model.addAttribute("isBuyer", "USER".equals(role));
		model.addAttribute("isGuest", role.isEmpty());
		model.addAttribute("cardMode", cardModeOf(role));
		return "category";
	}

	private List<CategoryCardRes> categoryCards() {
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

	private static String cardModeOf(String role) {
		return "USER".equals(role) ? "buy" : (role.isEmpty() ? "guest" : "view");
	}

	private String roleOf(HttpSession session) {
		Object u = session.getAttribute("user");
		if (!(u instanceof User user) || user.getRole() == null || user.getRole().getName() == null) {
			return "";
		}
		return user.getRole().getName().trim().toUpperCase();
	}

	@GetMapping("/menu")
	public String menu(@RequestParam(name = "keyword", required = false) String keyword,
			@RequestParam(name = "categoryId", required = false) Integer categoryId,
			@RequestParam(name = "sortBy", defaultValue = "default") String sortBy, HttpSession session, Model model) {

		if (categoryId != null && categoryId > 0) {
			return "redirect:/category/" + categoryId;
		}

		String role = roleOf(session);

		keyword = keyword == null ? "" : keyword.trim();

		if (!List.of("default", "newest", "sold", "price_asc", "price_desc").contains(sortBy)) {
			sortBy = "default";
		}

		List<Product> products = productRepository.filterProducts(keyword.isEmpty() ? null : keyword, null,
				"default".equals(sortBy) ? "id" : sortBy);
		if ("default".equals(sortBy)) {
			products = products.stream().sorted(Comparator.comparing(Product::getId)).toList();
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

		model.addAttribute("sections", sections);
		model.addAttribute("totalItems", cards.size());
		model.addAttribute("keyword", keyword);
		model.addAttribute("sortBy", sortBy);
		model.addAttribute("isBuyer", "USER".equals(role));
		model.addAttribute("isGuest", role.isEmpty());
		model.addAttribute("cardMode", cardModeOf(role));
		return "menu";
	}
}