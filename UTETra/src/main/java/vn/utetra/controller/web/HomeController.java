package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.dto.response.MenuSectionRes;
import vn.utetra.dto.response.OrderTab;
import vn.utetra.entity.Category;
import vn.utetra.entity.User;

import vn.utetra.service.CatalogService;
import vn.utetra.service.OrderFlowService;
import vn.utetra.service.UserService;
import vn.utetra.service.ProductHighlightService;
import vn.utetra.service.ViewedProductService;

@Controller
public class HomeController {

	private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

	private static final int PAGE_SIZE = 20;

	private static final int GUEST_MIN_SOLD = 10;

	private static final int HIGHLIGHT_PAGE_SIZE = 8;

	private final UserService userService;
	private final CatalogService catalogService;
	private final ProductHighlightService productHighlightService;
	private final OrderFlowService orderFlowService;
	private final ViewedProductService viewedProductService;
	private final vn.utetra.service.ReviewService reviewService;

	public HomeController(UserService userService, CatalogService catalogService,
			ProductHighlightService productHighlightService, OrderFlowService orderFlowService,
			ViewedProductService viewedProductService, vn.utetra.service.ReviewService reviewService) {
		this.userService = userService;
		this.catalogService = catalogService;
		this.productHighlightService = productHighlightService;
		this.orderFlowService = orderFlowService;
		this.viewedProductService = viewedProductService;
		this.reviewService = reviewService;
	}

	@GetMapping("/")
	public String index(@RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		boolean isLoggedIn = authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(authentication.getName());

		if (isLoggedIn) {

			Optional<User> userOptional = userService.findByUsername(authentication.getName());

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
		Optional<User> userOptional = userService.findByUsername(authentication.getName());
		if (userOptional.isEmpty()) {
			return "redirect:/";
		}
		User user = userOptional.get();
		session.setAttribute("user", user);
		if (Boolean.TRUE.equals(user.getMustChangePassword())) {
			if (user.getTempPasswordExpiresAt() != null && user.getTempPasswordExpiresAt().isBefore(java.time.LocalDateTime.now())) {
				session.invalidate();
				SecurityContextHolder.clearContext();
				return "redirect:/auth/login?signedOut=tempExpired";
			}
			return "redirect:/account/change-password";
		}
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

	private String guestHome(int page, Model model) {
		CatalogService.CardPage best = catalogService.bestSellers(GUEST_MIN_SOLD, page, PAGE_SIZE);
		addBranches(model);

		model.addAttribute("products", best.cards());
		model.addAttribute("currentPage", best.page());
		model.addAttribute("totalPages", best.totalPages());
		model.addAttribute("totalItems", best.totalItems());
		model.addAttribute("rankOffset", best.page() * PAGE_SIZE);
		model.addAttribute("minSold", GUEST_MIN_SOLD);
		model.addAttribute("isBuyer", false);
		model.addAttribute("isGuest", true);
		model.addAttribute("cardMode", "guest");
		return "home";
	}

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

		model.addAttribute("categories", catalogService.categoryCards());
		addBranches(model);

		model.addAttribute("newestPage", productHighlightService.page("newest", 0, HIGHLIGHT_PAGE_SIZE));
		model.addAttribute("soldPage", productHighlightService.page("sold", 0, HIGHLIGHT_PAGE_SIZE));
		model.addAttribute("ratingPage", productHighlightService.page("rating", 0, HIGHLIGHT_PAGE_SIZE));
		model.addAttribute("favoritePage", productHighlightService.page("favorite", 0, HIGHLIGHT_PAGE_SIZE));

		model.addAttribute("isBuyer", isBuyer);
		model.addAttribute("isGuest", false);
		model.addAttribute("cardMode", cardModeOf(role));
		return "user/home";
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

		Optional<Category> categoryOpt = catalogService.findCategory(id);
		if (categoryOpt.isEmpty()) {
			return "redirect:/menu#thuc-don";
		}

		if (!CatalogService.CATEGORY_SORTS.contains(sortBy)) {
			sortBy = "sold";
		}
		CatalogService.CardPage result = catalogService.categoryProducts(id, sortBy, page, PAGE_SIZE);

		String role = roleOf(session);
		model.addAttribute("category", categoryOpt.get());
		model.addAttribute("categories", catalogService.categoryCards());
		model.addAttribute("products", result.cards());
		model.addAttribute("sortBy", sortBy);
		model.addAttribute("currentPage", result.page());
		model.addAttribute("totalPages", result.totalPages());
		model.addAttribute("totalItems", result.totalItems());
		model.addAttribute("isBuyer", "USER".equals(role));
		model.addAttribute("isGuest", role.isEmpty());
		model.addAttribute("cardMode", cardModeOf(role));
		return "category";
	}

	private void addBranches(Model model) {
		CatalogService.BranchOverview overview = catalogService.branchOverview();
		model.addAttribute("branches", overview.branches());
		model.addAttribute("branchReviewSummaries", overview.reviewSummaries());
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

		if (!CatalogService.MENU_SORTS.contains(sortBy)) {
			sortBy = "default";
		}
		List<MenuSectionRes> sections = catalogService.menuSections(keyword, sortBy);
		int totalItems = sections.stream().mapToInt(x -> x.items().size()).sum();

		model.addAttribute("sections", sections);
		model.addAttribute("totalItems", totalItems);
		model.addAttribute("keyword", keyword);
		model.addAttribute("sortBy", sortBy);
		model.addAttribute("isBuyer", "USER".equals(role));
		model.addAttribute("isGuest", role.isEmpty());
		model.addAttribute("cardMode", cardModeOf(role));
		return "menu";
	}
}