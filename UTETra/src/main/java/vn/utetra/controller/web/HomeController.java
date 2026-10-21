package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import vn.utetra.entity.User;
import vn.utetra.repository.UserRepository;

@Controller
public class HomeController {

	@Autowired
	private UserRepository userRepository;

	@GetMapping("/")
	public String index(HttpSession session) {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		// Kiểm tra người dùng đã đăng nhập hay chưa
		if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {

			String username = auth.getName();

			Optional<User> userOpt = userRepository.findByUsername(username);

			if (userOpt.isPresent()) {

				User user = userOpt.get();

				// Lưu thông tin người dùng vào Session
				session.setAttribute("user", user);

				if (user.getRole() != null && user.getRole().getName() != null) {

					String roleName = user.getRole().getName().toUpperCase();

					// ADMIN: quản lý toàn hệ thống
					if ("ADMIN".equals(roleName)) {
						return "redirect:/admin/products";
					}

					// MANAGER: quản lý chi nhánh của mình -> Quầy đơn hàng
					if ("MANAGER".equals(roleName)) {
						return "redirect:/manager/orders";
					}
				}
			}
		}

		// Khách chưa đăng nhập hoặc USER thông thường
		// Chuyển sang trang thực đơn công khai
		return "redirect:/menu";
	}
}
