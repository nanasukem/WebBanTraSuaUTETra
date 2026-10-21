
package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.entity.Category;
import vn.utetra.entity.Product;
import vn.utetra.entity.User;

import vn.utetra.repository.CategoryRepository;
import vn.utetra.repository.ProductRepository;
import vn.utetra.repository.UserRepository;

@Controller
public class HomeController {

	private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

	private static final int PAGE_SIZE = 20;

	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;

	public HomeController(UserRepository userRepository, ProductRepository productRepository,
			CategoryRepository categoryRepository) {
		this.userRepository = userRepository;
		this.productRepository = productRepository;
		this.categoryRepository = categoryRepository;
	}

	/**
	 * Trang chủ: Guest -> home.html USER -> /menu ADMIN -> /admin/dashboard MANAGER
	 * -> /manager/dashboard
	 */
	@GetMapping("/")
	public String index(HttpSession session, Model model) {

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

				if ("ADMIN".equalsIgnoreCase(roleName)) {
					return "redirect:/admin/dashboard";
				}

				if ("MANAGER".equalsIgnoreCase(roleName)) {
					return "redirect:/manager/dashboard";
				}

				if ("USER".equalsIgnoreCase(roleName)) {
					return "redirect:/menu";
				}

			} else {
				session.removeAttribute("user");
			}

		} else {
			session.removeAttribute("user");
		}

		// Sản phẩm bán chạy dành cho Guest.
		List<Product> bestSellerProducts = Collections.emptyList();

		try {
			List<Product> products = productRepository.filterProducts(null, null, "sold");

			if (products != null) {
				bestSellerProducts = products.stream().filter(Objects::nonNull).limit(8).toList();
			}

		} catch (Exception e) {
			logger.error("Không thể tải danh sách sản phẩm bán chạy", e);
		}

		// Sản phẩm mới nhất dành cho Guest.
		List<Product> newestProducts = Collections.emptyList();

		try {
			List<Product> products = productRepository.filterProducts(null, null, "newest");

			if (products != null) {
				newestProducts = products.stream().filter(Objects::nonNull).limit(8).toList();
			}

		} catch (Exception e) {
			logger.error("Không thể tải danh sách sản phẩm mới nhất", e);
		}

		model.addAttribute("bestSellerProducts", bestSellerProducts);
		model.addAttribute("newestProducts", newestProducts);
		model.addAttribute("isBuyer", false);
		model.addAttribute("isGuest", true);

		return "home";
	}

	/**
	 * Trang thực đơn dành cho USER.
	 *
	 * Ví dụ: /menu /menu?page=1 /menu?keyword=tra&sortBy=sold&page=0
	 * /menu?categoryId=1&sortBy=price_asc&page=0
	 */
	@GetMapping("/menu")
	public String menu(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer categoryId, @RequestParam(defaultValue = "newest") String sortBy,
			@RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {

		// Chuẩn hóa từ khóa tìm kiếm.
		keyword = keyword == null ? "" : keyword.trim();

		if (keyword.isEmpty()) {
			keyword = null;
		}

		// Bỏ qua mã danh mục không hợp lệ.
		if (categoryId != null && categoryId <= 0) {
			categoryId = null;
		}

		// Chỉ chấp nhận các kiểu sắp xếp hợp lệ.
		List<String> allowedSorts = List.of("newest", "sold", "price_asc", "price_desc");

		if (sortBy == null || !allowedSorts.contains(sortBy)) {
			sortBy = "newest";
		}

		// Trang bắt đầu từ 0; không chấp nhận số trang âm.
		page = Math.max(page, 0);

		Pageable pageable = PageRequest.of(page, PAGE_SIZE);

		Page<Product> productPage = productRepository.filterProductsPaged(keyword, categoryId, sortBy, pageable);

		// Nếu URL yêu cầu vượt quá trang cuối, chuyển về trang cuối.
		if (productPage.getTotalPages() > 0 && page >= productPage.getTotalPages()) {

			page = productPage.getTotalPages() - 1;

			pageable = PageRequest.of(page, PAGE_SIZE);

			productPage = productRepository.filterProductsPaged(keyword, categoryId, sortBy, pageable);
		}

		// Danh mục phục vụ bộ lọc trên menu.html.
		List<Category> categories = categoryRepository.findAll();

		// Giữ tên thuộc tính products để tương thích template hiện tại.
		model.addAttribute("products", productPage.getContent());
		model.addAttribute("productPage", productPage);
		model.addAttribute("categories", categories);

		model.addAttribute("keyword", keyword == null ? "" : keyword);

		model.addAttribute("selectedCategory", categoryId);
		model.addAttribute("sortBy", sortBy);

		model.addAttribute("currentPage", page);
		model.addAttribute("pageSize", PAGE_SIZE);
		model.addAttribute("totalPages", productPage.getTotalPages());
		model.addAttribute("totalItems", productPage.getTotalElements());

		model.addAttribute("isBuyer", true);
		model.addAttribute("isGuest", false);

		// Giữ session.user để layout và các chức năng hiện tại
		// như giỏ hàng có thể tiếp tục sử dụng.

		return "menu";
	}
}
