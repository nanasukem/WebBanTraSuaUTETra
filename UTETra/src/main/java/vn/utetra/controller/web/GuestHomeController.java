package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.utetra.entity.User;
import vn.utetra.service.ProductService;

@Controller
public class GuestHomeController {

	@Autowired
	private ProductService productService;

	@GetMapping("/menu")
	public String viewMenu(Model model, HttpSession session, @RequestParam(required = false) String keyword,
			@RequestParam(required = false) Integer categoryId, @RequestParam(required = false) String sortBy) {

		// Lấy người dùng từ Session nếu đã đăng nhập
		User sessionUser = (User) session.getAttribute("user");

		if (sessionUser != null) {
			model.addAttribute("currentUser", sessionUser);
		}

		// Lấy danh mục
		model.addAttribute("categories", productService.getAllCategories());

		// Lấy danh sách sản phẩm theo bộ lọc
		model.addAttribute("products", productService.filterProducts(keyword, categoryId, sortBy));

		// Truyền dữ liệu bộ lọc sang giao diện
		model.addAttribute("selectedCategory", categoryId);
		model.addAttribute("keyword", keyword);
		model.addAttribute("sortBy", sortBy != null ? sortBy : "newest");

		// Sử dụng templates/index.html
		return "index";
	}
}
