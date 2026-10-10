package vn.utetra.controller.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.User;
import vn.utetra.service.AuthenticationService;
import vn.utetra.service.JwtService;

@Controller
@RequestMapping("/auth")
public class AuthController {

	private final AuthenticationService authenticationService;
	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	public AuthController(AuthenticationService authenticationService, JwtService jwtService,
			UserDetailsService userDetailsService) {
		this.authenticationService = authenticationService;
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	// 1. ĐĂNG NHẬP
	@GetMapping("/login")
	public String loginPage(Model model) {
		model.addAttribute("loginRequest", new LoginRequest());
		return "auth/login";
	}

	@PostMapping("/login")
	public String handleLogin(@ModelAttribute("loginRequest") LoginRequest loginRequest, HttpServletResponse response,
			HttpSession session, Model model) {
		try {
			User authenticatedUser = authenticationService.authenticate(loginRequest);

			// Sinh JWT Token
			UserDetails userDetails = userDetailsService.loadUserByUsername(authenticatedUser.getUsername());
			String token = jwtService.generateToken(userDetails);

			// Lưu Token vào Cookie HTTP-Only cho Thymeleaf
			Cookie jwtCookie = new Cookie("JWT_TOKEN", token);
			jwtCookie.setHttpOnly(true);
			jwtCookie.setPath("/");
			jwtCookie.setMaxAge((int) (jwtService.getExpirationTime() / 1000));
			response.addCookie(jwtCookie);

			session.setAttribute("currentUser", authenticatedUser);

			return "redirect:/menu";
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "auth/login";
		}
	}

	// 2. ĐĂNG KÝ
	@GetMapping("/register")
	public String registerPage(Model model) {
		model.addAttribute("registerRequest", new RegisterRequest());
		return "auth/register";
	}

	@PostMapping("/register")
	public String handleRegister(@ModelAttribute("registerRequest") RegisterRequest registerRequest, Model model) {
		try {
			User user = authenticationService.signup(registerRequest);
			return "redirect:/auth/verify-otp?email=" + user.getEmail();
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "auth/register";
		}
	}

	// 3. XÁC THỰC OTP ĐĂNG KÝ
	@GetMapping("/verify-otp")
	public String verifyOtpPage(@RequestParam("email") String email, Model model) {
		model.addAttribute("email", email);
		return "auth/verify-otp";
	}

	@PostMapping("/verify-otp")
	public String handleVerifyOtp(@RequestParam("email") String email, @RequestParam("code") String code, Model model) {
		try {
			authenticationService.verifyOtp(email, code);
			model.addAttribute("success", "Kích hoạt tài khoản thành công! Vui lòng đăng nhập.");
			model.addAttribute("loginRequest", new LoginRequest());
			return "auth/login";
		} catch (Exception e) {
			model.addAttribute("email", email);
			model.addAttribute("error", e.getMessage());
			return "auth/verify-otp";
		}
	}

	// 4. QUÊN MẬT KHẨU (Gửi OTP)
	@GetMapping("/forgot-password")
	public String forgotPasswordPage() {
		return "auth/forgot-password";
	}

	@PostMapping("/forgot-password")
	public String handleForgotPassword(@RequestParam("email") String email, Model model) {
		try {
			authenticationService.processForgotPassword(email);
			return "redirect:/auth/reset-password?email=" + email;
		} catch (Exception e) {
			model.addAttribute("error", e.getMessage());
			return "auth/forgot-password";
		}
	}

	// 5. ĐẶT LẠI MẬT KHẨU MỚI (Xác nhận OTP)
	@GetMapping("/reset-password")
	public String resetPasswordPage(@RequestParam("email") String email, Model model) {
		model.addAttribute("email", email);
		return "auth/reset-password";
	}

	@PostMapping("/reset-password")
	public String handleResetPassword(@RequestParam("email") String email, @RequestParam("code") String code,
			@RequestParam("newPassword") String newPassword, Model model) {
		try {
			authenticationService.resetPassword(email, code, newPassword);
			model.addAttribute("success", "Đặt lại mật khẩu thành công! Vui lòng đăng nhập.");
			model.addAttribute("loginRequest", new LoginRequest());
			return "auth/login";
		} catch (Exception e) {
			model.addAttribute("email", email);
			model.addAttribute("error", e.getMessage());
			return "auth/reset-password";
		}
	}

	// 6. ĐĂNG XUẤT (Xóa Cookie JWT & Hủy Session)
	@GetMapping("/logout")
	public String logout(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
		// Xóa Cookie JWT_TOKEN
		Cookie jwtCookie = new Cookie("JWT_TOKEN", null);
		jwtCookie.setPath("/");
		jwtCookie.setHttpOnly(true);
		jwtCookie.setMaxAge(0);
		response.addCookie(jwtCookie);

		// Hủy Session
		if (session != null) {
			session.invalidate();
		}

		// Xóa Security Context
		SecurityContextHolder.clearContext();

		return "redirect:/auth/login?logout=true";
	}
}