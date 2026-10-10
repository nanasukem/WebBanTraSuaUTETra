package vn.utetra.controller.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.User;
import vn.utetra.service.AuthService;

@Controller
@RequestMapping("/auth")
public class AuthController {

	@Autowired
	private AuthService authService;

	@GetMapping("/login")
	public String loginPage(Model model) {
		model.addAttribute("loginRequest", new LoginRequest());
		return "auth/login";
	}

	@PostMapping("/login")
	public String handleLogin(@ModelAttribute("loginRequest") LoginRequest loginRequest, HttpSession session,
			Model model) {
		try {
			User user = authService.login(loginRequest);
			session.setAttribute("currentUser", user);
			return "redirect:/menu";
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "auth/login";
		}
	}

	@GetMapping("/register")
	public String registerPage(Model model) {
		model.addAttribute("registerRequest", new RegisterRequest());
		return "auth/register";
	}

	@PostMapping("/register")
	public String handleRegister(@ModelAttribute("registerRequest") RegisterRequest registerRequest, Model model) {
		try {
			authService.register(registerRequest);
			model.addAttribute("success", "Đăng ký tài khoản thành công! Vui lòng đăng nhập.");
			model.addAttribute("loginRequest", new LoginRequest());
			return "auth/login";
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "auth/register";
		}
	}

	@GetMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/auth/login";
	}
}