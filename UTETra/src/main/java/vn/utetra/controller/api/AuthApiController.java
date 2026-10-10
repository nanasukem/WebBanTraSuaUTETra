package vn.utetra.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.User;
import vn.utetra.service.AuthenticationService;
import vn.utetra.service.JwtService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

	private final AuthenticationService authenticationService;
	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;

	public AuthApiController(AuthenticationService authenticationService, JwtService jwtService,
			UserDetailsService userDetailsService) {
		this.authenticationService = authenticationService;
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
	}

	// 1. API ĐĂNG KÝ
	@PostMapping("/signup")
	public ResponseEntity<?> register(@RequestBody RegisterRequest registerUser) {
		try {
			User registeredUser = authenticationService.signup(registerUser);
			Map<String, Object> response = new HashMap<>();
			response.put("message", "Đăng ký thành công! Vui lòng kiểm tra email để lấy mã OTP.");
			response.put("email", registeredUser.getEmail());
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	// 2. API XÁC THỰC OTP ĐĂNG KÝ
	@PostMapping("/verify-otp")
	public ResponseEntity<?> verifyOtp(@RequestParam("email") String email, @RequestParam("code") String code) {
		try {
			authenticationService.verifyOtp(email, code);
			return ResponseEntity.ok(Map.of("message", "Kích hoạt tài khoản thành công! Vui lòng đăng nhập."));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	// 3. API ĐĂNG NHẬP (Trả về Token JWT)
	@PostMapping("/login")
	public ResponseEntity<?> authenticate(@RequestBody LoginRequest loginUser) {
		try {
			User authenticatedUser = authenticationService.authenticate(loginUser);

			UserDetails userDetails = userDetailsService.loadUserByUsername(authenticatedUser.getUsername());
			String jwtToken = jwtService.generateToken(userDetails);

			Map<String, Object> response = new HashMap<>();
			response.put("token", jwtToken);
			response.put("expiresIn", jwtService.getExpirationTime());
			response.put("username", authenticatedUser.getUsername());
			response.put("role", authenticatedUser.getRole().getName());

			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	// 4. API QUÊN MẬT KHẨU (Gửi OTP qua email)
	@PostMapping("/forgot-password")
	public ResponseEntity<?> forgotPassword(@RequestParam("email") String email) {
		try {
			authenticationService.processForgotPassword(email);
			return ResponseEntity.ok(Map.of("message", "Mã OTP khôi phục mật khẩu đã được gửi đến email của bạn."));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	// 5. API ĐẶT LẠI MẬT KHẨU MỚI
	@PostMapping("/reset-password")
	public ResponseEntity<?> resetPassword(@RequestParam("email") String email, @RequestParam("code") String code,
			@RequestParam("newPassword") String newPassword) {
		try {
			authenticationService.resetPassword(email, code, newPassword);
			return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công!"));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	// 6. API ĐĂNG XUẤT
	@PostMapping("/logout")
	public ResponseEntity<?> logout(HttpServletResponse response) {
		// Xóa Cookie JWT nếu có
		Cookie jwtCookie = new Cookie("JWT_TOKEN", null);
		jwtCookie.setPath("/");
		jwtCookie.setHttpOnly(true);
		jwtCookie.setMaxAge(0);
		response.addCookie(jwtCookie);

		// Xóa Context Security hiện tại
		SecurityContextHolder.clearContext();

		return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công!"));
	}
}