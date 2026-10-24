package vn.utetra.service;

import vn.utetra.dto.response.MemberTierInfo;
import vn.utetra.entity.MemberTier;

import java.math.BigDecimal;

public interface MemberService {

	MemberTier getCurrentTier(Integer userId);

	MemberTierInfo getTierInfo(Integer userId);

	BigDecimal calculateDiscount(MemberTier tier, BigDecimal base);

	boolean isNewCustomer(Integer userId);

	java.util.List<vn.utetra.entity.MemberTier> allTiers();
}