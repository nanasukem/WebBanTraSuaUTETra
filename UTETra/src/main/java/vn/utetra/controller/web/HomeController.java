package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.utetra.entity.User;
import vn.utetra.repository.UserRepository;
import vn.utetra.service.ProductService;

import java.util.Optional;

@Controller
public class HomeController {

	@Autowired
	private ProductService productService;

	@Autowired
	private UserRepository userRepository;

	@GetMapping({ "/", "/menu" })
	public String viewMenu(Model model, HttpSession session, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer categoryId, @RequestParam(required = false) String sortBy) {

		// Xử lý thông tin người dùng đăng nhập
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
			String username = auth.getName();
			Optional<User> userOpt = userRepository.findByUsername(username);
			if (userOpt.isPresent()) {
				model.addAttribute("currentUser", userOpt.get());
				session.setAttribute("user", userOpt.get());
			}
		} else {
			User sessionUser = (User) session.getAttribute("user");
			if (sessionUser != null) {
				model.addAttribute("currentUser", sessionUser);
			}
		}

		model.addAttribute("categories", productService.getAllCategories());
		model.addAttribute("products", productService.filterProducts(keyword, categoryId, sortBy));
		model.addAttribute("selectedCategory", categoryId);
		model.addAttribute("keyword", keyword);
		model.addAttribute("sortBy", sortBy);

		return "admin/products/menu";
	}
}