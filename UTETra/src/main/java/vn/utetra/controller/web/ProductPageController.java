package vn.utetra.controller.web;

import java.security.Principal;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.dto.response.ReviewableItemRes;
import vn.utetra.entity.User;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.service.FavoriteService;
import vn.utetra.service.ProductService;
import vn.utetra.service.ReviewService;
import vn.utetra.service.ViewedProductService;

@Controller
public class ProductPageController {

	private final ProductService productService;
	private final ReviewService reviewService;
	private final FavoriteService favoriteService;
	private final ViewedProductService viewedProductService;

	public ProductPageController(ProductService productService, ReviewService reviewService,
			FavoriteService favoriteService, ViewedProductService viewedProductService) {
		this.productService = productService;
		this.reviewService = reviewService;
		this.favoriteService = favoriteService;
		this.viewedProductService = viewedProductService;
	}

	@GetMapping("/products/{id}")
	public String detail(@PathVariable("id") Integer id, @RequestParam(name = "od", required = false) Integer od,
			Principal principal, HttpSession session, Model model) {
		try {
			model.addAttribute("product", productService.getDetail(id));
		} catch (ResourceNotFoundException e) {
			return "redirect:/menu";
		}

		String role = roleOf(session);
		boolean isBuyer = "USER".equals(role) && principal != null;
		Integer userId = isBuyer ? ((User) session.getAttribute("user")).getId() : null;

		model.addAttribute("reviews", reviewService.listByProduct(id));
		model.addAttribute("related", productService.related(id, 8));
		model.addAttribute("isBuyer", isBuyer);
		model.addAttribute("isGuest", role.isEmpty());
		model.addAttribute("cardMode", isBuyer ? "buy" : (role.isEmpty() ? "guest" : "view"));

		if (isBuyer) {
			viewedProductService.record(userId, id);
			List<ReviewableItemRes> purchased = reviewService.purchasedItems(userId, id);
			List<ReviewableItemRes> pending = reviewService.pendingItems(userId, id);
			model.addAttribute("isFavorite", favoriteService.isFavorite(userId, id));
			model.addAttribute("purchased", purchased);
			model.addAttribute("pendingItems", pending);
			model.addAttribute("selectedOd", pending.stream().anyMatch(i -> i.orderDetailId().equals(od)) ? od
					: (pending.isEmpty() ? null : pending.get(0).orderDetailId()));
		} else {
			model.addAttribute("isFavorite", false);
			model.addAttribute("purchased", List.of());
			model.addAttribute("pendingItems", List.of());
		}
		return "products/detail";
	}

	private String roleOf(HttpSession session) {
		Object u = session.getAttribute("user");
		if (!(u instanceof User user) || user.getRole() == null || user.getRole().getName() == null) {
			return "";
		}
		return user.getRole().getName().trim().toUpperCase();
	}
}
