package vn.utetra.service;

import vn.utetra.dto.response.MemberTierInfo;
import vn.utetra.entity.MemberTier;

import java.math.BigDecimal;

public interface MemberService {

	/** Hạng hiện tại: theo tổng tiền đơn ĐÃ GIAO trong 12 tháng gần nhất (tự lên/xuống hạng) */
	MemberTier getCurrentTier(Integer userId);

	/** Hạng hiện tại + còn bao nhiêu để lên hạng (cho trang hồ sơ, checkout) */
	MemberTierInfo getTierInfo(Integer userId);

	/** Tiền giảm theo hạng, tính trên base (tiền món sau khi trừ mã giảm món) */
	BigDecimal calculateDiscount(MemberTier tier, BigDecimal base);

	/** Tài khoản mới = chưa có đơn nào (không tính đơn đã huỷ) */
	boolean isNewCustomer(Integer userId);
}