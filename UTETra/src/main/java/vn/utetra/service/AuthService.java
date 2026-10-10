package vn.utetra.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.utetra.dto.request.LoginRequest;
import vn.utetra.dto.request.RegisterRequest;
import vn.utetra.entity.Role;
import vn.utetra.entity.User;
import vn.utetra.repository.RoleRepository;
import vn.utetra.repository.UserRepository;

@Service
public class AuthService {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	public User register(RegisterRequest request) {
		if (userRepository.existsByUsername(request.getUsername())) {
			throw new RuntimeException("Tên đăng nhập đã tồn tại!");
		}
		if (userRepository.existsByEmail(request.getEmail())) {
			throw new RuntimeException("Email đã được sử dụng!");
		}

		User user = new User();
		user.setUsername(request.getUsername());
		user.setPassword(passwordEncoder.encode(request.getPassword()));
		user.setFullName(request.getFullName());
		user.setEmail(request.getEmail());
		user.setPhone(request.getPhone());
		user.setIsVerified(true);
		user.setIsActive(true);

		Role userRole = roleRepository.findByName("USER")
				.orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò USER"));
		user.setRole(userRole);

		return userRepository.save(user);
	}

	public User login(LoginRequest request) {
		User user = userRepository.findByUsername(request.getUsername())
				.orElseThrow(() -> new RuntimeException("Tài khoản hoặc mật khẩu không chính xác!"));

		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			throw new RuntimeException("Tài khoản hoặc mật khẩu không chính xác!");
		}

		if (!user.getIsActive()) {
			throw new RuntimeException("Tài khoản của bạn đã bị khóa!");
		}

		return user;
	}
}