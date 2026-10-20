package vn.utetra.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.utetra.entity.User;
import vn.utetra.repository.UserRepository;

import java.security.Principal;

/**
 * Lấy người dùng đang đăng nhập - từ phiên đăng nhập, KHÔNG tin dữ liệu trình
 * duyệt gửi lên
 */
@Service
public class CurrentUserService {

	private final UserRepository userRepository;

	public CurrentUserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User requireUser(Principal principal, HttpSession session) {
		// 1. Spring Security (form login / JWT)
		if (principal != null) {
			String name = principal.getName();
			return userRepository.findByUsernameOrEmail(name, name)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập"));
		}
		// 2. Dự phòng: user lưu trong session (trang menu đang dùng
		// session.currentUser)
		if (session != null && session.getAttribute("user") instanceof User u) {
			return userRepository.findById(u.getId())
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập"));
		}
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
	}

	public Integer requireUserId(Principal principal, HttpSession session) {
		return requireUser(principal, session).getId();
	}
}