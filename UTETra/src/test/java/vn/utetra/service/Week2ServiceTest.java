package vn.utetra.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.request.CarrierReq;
import vn.utetra.entity.Carrier;
import vn.utetra.exception.BusinessException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Test tuần 2 - mỗi test tự rollback, không làm bẩn DB */
@SpringBootTest
@Transactional
class Week2ServiceTest {

	@Autowired
	CarrierService carrierService;

	private CarrierReq carrierReq(String name, long fee) {
		CarrierReq r = new CarrierReq();
		r.setName(name);
		r.setFee(BigDecimal.valueOf(fee));
		return r;
	}

	// ================= NHÀ VẬN CHUYỂN =================
	@Test
	void carrier_themMoi_dangHoatDong() {
		Carrier c = carrierService.create(carrierReq("  JUnit Express  ", 18000));
		assertNotNull(c.getId());
		assertEquals("JUnit Express", c.getName(), "Tên phải được cắt khoảng trắng");
		assertTrue(c.getIsActive());
		assertTrue(carrierService.findActive().stream().anyMatch(x -> x.getId().equals(c.getId())));
	}

	@Test
	void carrier_trungTen_baoLoi() {
		assertThrows(BusinessException.class, () -> carrierService.create(carrierReq("giao hàng nhanh", 20000)));
	}

	@Test
	void carrier_capNhatPhi() {
		Carrier c = carrierService.create(carrierReq("JUnit Update", 15000));
		carrierService.update(c.getId(), carrierReq("JUnit Update", 22000));
		assertEquals(0, BigDecimal.valueOf(22000).compareTo(carrierService.findById(c.getId()).getFee()));
	}

	@Test
	void carrier_tat_khongConHienOCheckout() {
		Carrier c = carrierService.create(carrierReq("JUnit Toggle", 15000));
		carrierService.toggleActive(c.getId());
		assertFalse(carrierService.findById(c.getId()).getIsActive());
		assertTrue(carrierService.findActive().stream().noneMatch(x -> x.getId().equals(c.getId())));
		carrierService.toggleActive(c.getId()); // bật lại
		assertTrue(carrierService.findById(c.getId()).getIsActive());
	}

	@Test
	void carrier_timKiem() {
		assertEquals(1, carrierService.search("tiết kiệm").size()); // "Giao Hàng Tiết Kiệm" trong dữ liệu mẫu
		assertEquals(2, carrierService.search(null).size());
	}
}