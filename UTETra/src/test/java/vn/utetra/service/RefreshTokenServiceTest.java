package vn.utetra.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import vn.utetra.entity.RefreshToken;
import vn.utetra.entity.User;
import vn.utetra.repository.RefreshTokenRepository;
import vn.utetra.repository.UserRepository;

@SpringBootTest
@Transactional
class RefreshTokenServiceTest {

	@Autowired
	RefreshTokenService refreshTokenService;
	@Autowired
	RefreshTokenRepository refreshTokenRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	UserService userService;
	@Autowired
	EntityManager em;

	User user;

	@BeforeEach
	void setUp() {
		user = userRepository.findByUsername("user1").orElseThrow();
		user.setIsActive(true);
	}

	private void sync() {
		em.flush();
		em.clear();
	}

	private Map<?, ?> anHan() {
		return (Map<?, ?>) ReflectionTestUtils.getField(refreshTokenService, "recentlyRotated");
	}

	@Test
	void capToken_luuBanBam_khongLuuTokenGoc() {
		String raw = refreshTokenService.issue(user);
		sync();
		assertTrue(raw.length() >= 60);
		assertTrue(refreshTokenRepository.findByToken(raw).isEmpty(), "Database không được lưu token gốc");
		assertTrue(refreshTokenService.rotate(raw, true).isPresent());
	}

	@Test
	void lamMoi_capTokenMoi_vaThuHoiTokenCu() {
		String raw = refreshTokenService.issue(user);
		sync();
		Optional<RefreshTokenService.Rotation> r = refreshTokenService.rotate(raw, true);
		assertTrue(r.isPresent());
		assertEquals("user1", r.get().user().getUsername());
		assertNotNull(r.get().newToken());
		assertNotEquals(raw, r.get().newToken());
		sync();
		assertTrue(refreshTokenService.rotate(r.get().newToken(), true).isPresent(), "Token mới phải dùng được");
	}

	@Test
	void tokenCu_trongThoiGianAnHan_chiChoTrinhDuyet() {
		String raw = refreshTokenService.issue(user);
		sync();
		refreshTokenService.rotate(raw, true);
		sync();
		Optional<RefreshTokenService.Rotation> r = refreshTokenService.rotate(raw, true);
		assertTrue(r.isPresent(), "Request song song trong 30 giây vẫn được cho qua");
		assertNull(r.get().newToken(), "Ân hạn không cấp thêm refresh token mới");
		assertTrue(refreshTokenService.rotate(raw, false).isEmpty(), "API client không được ân hạn");
	}

	@Test
	void tokenCu_dungLaiSauAnHan_thuHoiToanBo() {
		String raw = refreshTokenService.issue(user);
		sync();
		String moi = refreshTokenService.rotate(raw, true).orElseThrow().newToken();
		sync();
		anHan().clear();
		assertTrue(refreshTokenService.rotate(raw, true).isEmpty(), "Token cũ bị dùng lại phải bị từ chối");
		sync();
		assertTrue(refreshTokenService.rotate(moi, true).isEmpty(), "Phát hiện dùng lại thì thu hồi cả token mới");
	}

	@Test
	void dangXuat_thuHoiToken_vaTokenKeTiep() {
		String raw = refreshTokenService.issue(user);
		sync();
		String moi = refreshTokenService.rotate(raw, true).orElseThrow().newToken();
		sync();
		refreshTokenService.revoke(raw);
		sync();
		assertTrue(refreshTokenService.rotate(moi, true).isEmpty());
		assertTrue(refreshTokenService.rotate(raw, true).isEmpty(), "Đã đăng xuất thì không còn ân hạn");
	}

	@Test
	void tokenHetHan_khongLamMoiDuoc() {
		String raw = refreshTokenService.issue(user);
		sync();
		RefreshToken t = refreshTokenRepository.findAll().stream()
				.filter(x -> x.getUser().getId().equals(user.getId()) && !x.getRevoked())
				.max((a, b) -> a.getId().compareTo(b.getId())).orElseThrow();
		t.setExpiresAt(LocalDateTime.now().minusMinutes(1));
		sync();
		assertTrue(refreshTokenService.rotate(raw, true).isEmpty());
	}

	@Test
	void taiKhoanBiKhoa_khongLamMoiDuoc() {
		String raw = refreshTokenService.issue(user);
		user.setIsActive(false);
		userRepository.save(user);
		sync();
		assertTrue(refreshTokenService.rotate(raw, true).isEmpty());
	}

	@Test
	void doiMatKhau_thuHoiMoiThietBi() {
		String a = refreshTokenService.issue(user);
		String b = refreshTokenService.issue(user);
		sync();
		userService.changePassword(user.getId(), "123456", "abc12345", "abc12345");
		sync();
		assertTrue(refreshTokenService.rotate(a, true).isEmpty());
		assertTrue(refreshTokenService.rotate(b, true).isEmpty());
	}

	@Test
	void tokenKhongHopLe_tuChoi() {
		assertTrue(refreshTokenService.rotate(null, true).isEmpty());
		assertTrue(refreshTokenService.rotate("", true).isEmpty());
		assertTrue(refreshTokenService.rotate("token-bia", true).isEmpty());
	}
}
