package vn.utetra.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import vn.utetra.entity.User;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class UserService {

	public static final int MIN_PASSWORD = 6;
	public static final int MAX_PASSWORD = 72;
	private static final String PHONE_REGEX = "0\\d{9}";
	private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final CloudinaryService cloudinaryService;
	private final RefreshTokenService refreshTokenService;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			CloudinaryService cloudinaryService, RefreshTokenService refreshTokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.cloudinaryService = cloudinaryService;
		this.refreshTokenService = refreshTokenService;
	}

	public java.util.Optional<User> findByUsername(String username) {
		return username == null ? java.util.Optional.empty() : userRepository.findByUsername(username);
	}

	public java.util.Optional<User> findById(Integer id) {
		return id == null ? java.util.Optional.empty() : userRepository.findById(id);
	}

	public List<User> shippersOfBranch(Integer branchId) {
		return userRepository.findShippersOfBranch(branchId);
	}

	public List<User> customers() {
		return userRepository.findByRole_Name("USER").stream()
				.sorted(java.util.Comparator.comparing(User::getUsername)).toList();
	}

	@Transactional
	public User clearTemporaryPassword(Integer userId) {
		User u = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
		u.setMustChangePassword(false);
		u.setTempPasswordExpiresAt(null);
		return userRepository.save(u);
	}

	public User getProfile(Integer userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
	}

	@Transactional
	public User updateProfile(Integer userId, String fullName, String phone, MultipartFile avatar) {
		User u = getProfile(userId);
		String name = fullName == null ? "" : fullName.trim().replaceAll("\\s+", " ");
		if (name.isEmpty()) {
			throw new BusinessException("Vui lòng nhập họ và tên");
		}
		if (name.length() > 100) {
			throw new BusinessException("Họ và tên tối đa 100 ký tự");
		}
		String p = UserRepository.normalizePhone(phone);
		if (p.isEmpty()) {
			throw new BusinessException("Vui lòng nhập số điện thoại");
		}
		if (!p.matches(PHONE_REGEX)) {
			throw new BusinessException("Số điện thoại phải có 10 số, bắt đầu bằng 0");
		}
		if (userRepository.existsByPhoneAndIdNot(p, userId)) {
			throw new BusinessException("Số điện thoại đã được dùng cho tài khoản khác");
		}

		if (avatar != null && !avatar.isEmpty()) {
			String ct = avatar.getContentType() == null ? "" : avatar.getContentType().toLowerCase();
			if (!ct.startsWith("image/")) {
				throw new BusinessException("Ảnh đại diện phải là file ảnh");
			}
			if (avatar.getSize() > MAX_AVATAR_BYTES) {
				throw new BusinessException("Ảnh đại diện tối đa 5MB");
			}
			String old = u.getAvatarCloudinaryId();
			String url;
			try {
				url = cloudinaryService.uploadImage(avatar, "avatars");
			} catch (RuntimeException e) {
				throw new BusinessException("Không tải được ảnh đại diện, vui lòng thử lại");
			}
			u.setAvatarCloudinaryId(url);
			if (old != null && !old.equals(url)) {
				cloudinaryService.deleteImage(old);
			}
		}

		u.setFullName(name);
		u.setPhone(p);
		return userRepository.saveAndFlush(u);
	}

	@Transactional
	public void changePassword(Integer userId, String currentPassword, String newPassword, String confirmPassword) {
		User u = getProfile(userId);
		if (currentPassword == null || currentPassword.isEmpty()
				|| !passwordEncoder.matches(currentPassword, u.getPassword())) {
			throw new BusinessException("Mật khẩu hiện tại không đúng");
		}
		if (newPassword == null || newPassword.length() < MIN_PASSWORD) {
			throw new BusinessException("Mật khẩu mới tối thiểu " + MIN_PASSWORD + " ký tự");
		}
		if (newPassword.length() > MAX_PASSWORD) {
			throw new BusinessException("Mật khẩu mới tối đa " + MAX_PASSWORD + " ký tự");
		}
		if (!newPassword.equals(confirmPassword)) {
			throw new BusinessException("Nhập lại mật khẩu mới không khớp");
		}
		if (passwordEncoder.matches(newPassword, u.getPassword())) {
			throw new BusinessException("Mật khẩu mới phải khác mật khẩu hiện tại");
		}
		u.setPassword(passwordEncoder.encode(newPassword));
		userRepository.saveAndFlush(u);
		refreshTokenService.revokeAllForUser(u.getId());
	}
}
