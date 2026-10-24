package vn.utetra.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utetra.dto.response.MemberTierInfo;
import vn.utetra.entity.MemberTier;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.exception.BusinessException;
import vn.utetra.repository.MemberTierRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.service.MemberService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MemberServiceImpl implements MemberService {

	public static final int REVIEW_MONTHS = 12;

	private final MemberTierRepository memberTierRepository;
	private final OrderRepository orderRepository;

	public MemberServiceImpl(MemberTierRepository memberTierRepository, OrderRepository orderRepository) {
		this.memberTierRepository = memberTierRepository;
		this.orderRepository = orderRepository;
	}

	@Override
	public MemberTier getCurrentTier(Integer userId) {
		BigDecimal spent = spentLastMonths(userId);
		MemberTier current = null;
		for (MemberTier t : tiers()) {
			if (current == null || spent.compareTo(t.getMinSpent()) >= 0) current = t;
		}
		return current;
	}

	@Override
	public MemberTierInfo getTierInfo(Integer userId) {
		BigDecimal spent = spentLastMonths(userId);
		List<MemberTier> list = tiers();
		int idx = 0;
		for (int i = 0; i < list.size(); i++) {
			if (spent.compareTo(list.get(i).getMinSpent()) >= 0) idx = i;
		}
		MemberTier current = list.get(idx);
		MemberTier next = idx + 1 < list.size() ? list.get(idx + 1) : null;
		return new MemberTierInfo(current.getName(), current.getDiscountPercent(), current.getMaxDiscount(), spent,
				next == null ? null : next.getName(),
				next == null ? BigDecimal.ZERO : next.getMinSpent().subtract(spent));
	}

	@Override
	public BigDecimal calculateDiscount(MemberTier tier, BigDecimal base) {
		if (tier == null || base == null || base.signum() <= 0 || tier.getDiscountPercent() == null
				|| tier.getDiscountPercent().signum() <= 0) {
			return BigDecimal.ZERO;
		}
		BigDecimal discount = base.multiply(tier.getDiscountPercent()).divide(BigDecimal.valueOf(100), 0,
				RoundingMode.DOWN);
		if (tier.getMaxDiscount() != null) discount = discount.min(tier.getMaxDiscount());
		return discount.min(base);
	}

	@Override
	public boolean isNewCustomer(Integer userId) {
		return !orderRepository.existsByUserIdAndStatusNot(userId, OrderStatus.CANCELLED);
	}

	private BigDecimal spentLastMonths(Integer userId) {
		BigDecimal sum = orderRepository.sumTotalByUserAndStatusSince(userId, OrderStatus.DELIVERED,
				LocalDateTime.now().minusMonths(REVIEW_MONTHS));
		return sum == null ? BigDecimal.ZERO : sum;
	}

	private List<MemberTier> tiers() {
		List<MemberTier> list = memberTierRepository.findAllByOrderByMinSpentAsc();
		if (list.isEmpty()) throw new BusinessException("Chưa cấu hình hạng thành viên (bảng MemberTiers trống)");
		return list;
	}
}