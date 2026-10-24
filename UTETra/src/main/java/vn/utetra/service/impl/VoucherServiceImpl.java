package vn.utetra.service.impl;

import vn.utetra.dto.request.VoucherReq;
import vn.utetra.dto.response.ManagerVoucherRes;
import vn.utetra.entity.enums.OrderStatus;
import vn.utetra.entity.Branch;
import vn.utetra.entity.MemberTier;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.VoucherUsage;
import vn.utetra.entity.enums.DiscountType;
import vn.utetra.entity.enums.PaymentMethod;
import vn.utetra.entity.enums.VoucherApplyTo;
import vn.utetra.entity.enums.VoucherAudience;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.OrderRepository;
import vn.utetra.repository.UserRepository;
import vn.utetra.repository.VoucherRepository;
import vn.utetra.repository.VoucherUsageRepository;
import vn.utetra.service.MemberService;
import vn.utetra.service.VoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class VoucherServiceImpl implements VoucherService {

	private final VoucherRepository voucherRepository;
	private final BranchRepository branchRepository;
	private final VoucherUsageRepository voucherUsageRepository;
	private final UserRepository userRepository;
	private final OrderRepository orderRepository;
	private final MemberService memberService;
	private final vn.utetra.service.VoucherAlertService voucherAlertService;

	public VoucherServiceImpl(VoucherRepository voucherRepository, BranchRepository branchRepository,
			VoucherUsageRepository voucherUsageRepository, UserRepository userRepository,
			OrderRepository orderRepository, MemberService memberService,
			vn.utetra.service.VoucherAlertService voucherAlertService) {
		this.voucherAlertService = voucherAlertService;
		this.voucherRepository = voucherRepository;
		this.branchRepository = branchRepository;
		this.voucherUsageRepository = voucherUsageRepository;
		this.userRepository = userRepository;
		this.orderRepository = orderRepository;
		this.memberService = memberService;
	}

	@Override
	public List<Voucher> findAppVouchers() {
		return voucherRepository.findByBranchIsNullOrderByCreatedAtDesc().stream()
				.filter(v -> v.getApplyTo() == VoucherApplyTo.PRODUCT).toList();
	}

	@Override
	public List<Voucher> findByBranch(Integer branchId) {
		return voucherRepository.findByBranch_IdOrderByCreatedAtDesc(branchId).stream()
				.filter(v -> v.getApplyTo() == VoucherApplyTo.PRODUCT).toList();
	}

	@Override
	public List<Voucher> findAllVouchers() {
		return voucherRepository.findAllByOrderByCreatedAtDesc().stream()
				.filter(v -> v.getApplyTo() == VoucherApplyTo.PRODUCT).toList();
	}

	@Override
	public List<ManagerVoucherRes> branchView(Integer branchId) {
		Map<Integer, Object[]> stats = new HashMap<>();
		List<Object[]> rows = new ArrayList<>(orderRepository.productVoucherStatsOfBranch(branchId, OrderStatus.CANCELLED));
		for (Object[] r : rows) {
			stats.put(((Number) r[0]).intValue(), r);
		}
		LocalDateTime now = LocalDateTime.now();
		List<String> order = List.of("RUNNING", "UPCOMING", "SOLD_OUT", "OFF", "ENDED");
		return voucherRepository.findApplicableToBranch(branchId).stream()
				.filter(v -> v.getApplyTo() == VoucherApplyTo.PRODUCT)
				.map(v -> {
			Object[] s = stats.get(v.getId());
			String status = voucherStatus(v, now);
			return new ManagerVoucherRes(v.getId(), v.getCode(), v.getName(), v.getBranch() == null,
					"Giảm tiền món", discountText(v),
					conditionText(v), DAY.format(v.getStartDate()) + " – " + DAY.format(v.getEndDate()), status,
					STATUS_LABEL.get(status), STATUS_BADGE.get(status), nz(v.getQuantity()), nz(v.getUsedCount()),
					s == null ? 0 : ((Number) s[1]).longValue(),
					s == null ? BigDecimal.ZERO : new BigDecimal(s[2].toString()));
		}).sorted(Comparator.comparingInt((ManagerVoucherRes r) -> order.indexOf(r.status()))).toList();
	}

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final Map<String, String> STATUS_LABEL = Map.of("RUNNING", "Đang chạy", "UPCOMING", "Sắp diễn ra",
			"SOLD_OUT", "Hết lượt", "OFF", "Đã tắt", "ENDED", "Đã kết thúc");
	private static final Map<String, String> STATUS_BADGE = Map.of("RUNNING", "text-bg-success", "UPCOMING",
			"text-bg-info", "SOLD_OUT", "text-bg-warning", "OFF", "text-bg-secondary", "ENDED", "text-bg-light border");

	static String voucherStatus(Voucher v, LocalDateTime now) {
		if (!Boolean.TRUE.equals(v.getIsActive())) return "OFF";
		if (v.getEndDate() != null && v.getEndDate().isBefore(now)) return "ENDED";
		if (v.getStartDate() != null && v.getStartDate().isAfter(now)) return "UPCOMING";
		if (nz(v.getUsedCount()) >= nz(v.getQuantity())) return "SOLD_OUT";
		return "RUNNING";
	}

	private static String money(BigDecimal x) {
		return String.format("%,d", x.setScale(0, RoundingMode.HALF_UP).longValue()).replace(',', '.') + "đ";
	}

	private static String discountText(Voucher v) {
		if (v.getDiscountType() == DiscountType.PERCENT) {
			String s = v.getDiscountValue().stripTrailingZeros().toPlainString() + "%";
			return v.getMaxDiscount() == null ? s : s + " (tối đa " + money(v.getMaxDiscount()) + ")";
		}
		return money(v.getDiscountValue());
	}

	private static String conditionText(Voucher v) {
		List<String> parts = new ArrayList<>();
		parts.add(v.getMinOrderValue() == null || v.getMinOrderValue().signum() == 0 ? "Mọi đơn"
				: "Đơn từ " + money(v.getMinOrderValue()));
		if (v.getPerUserLimit() != null) parts.add("Mỗi khách " + v.getPerUserLimit() + " lần");
		if (v.getAudience() == VoucherAudience.NEW_USER) parts.add("Chỉ khách mới");
		if (v.getAudience() == VoucherAudience.MIN_TIER && v.getMinTier() != null)
			parts.add("Hạng " + v.getMinTier().getName() + " trở lên");
		if (v.getPaymentMethod() != null) parts.add("Chỉ " + (v.getPaymentMethod() == PaymentMethod.COD ? "COD" : v.getPaymentMethod().name()));
		return String.join(" · ", parts);
	}

	private static int nz(Integer x) {
		return x == null ? 0 : x;
	}

	@Override
	public Voucher findById(Integer id) {
		return voucherRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy voucher #" + id));
	}

	@Override
	@Transactional
	public Voucher create(VoucherReq req) {
		String code = req.getCode().trim().toUpperCase();
		if (voucherRepository.existsByCodeIgnoreCase(code)) {
			throw new BusinessException("Mã voucher " + code + " đã tồn tại");
		}
		Voucher v = new Voucher();
		copy(req, v);
		return voucherRepository.save(v);
	}

	@Override
	@Transactional
	public Voucher update(Integer id, VoucherReq req) {
		Voucher v = findById(id);
		String code = req.getCode().trim().toUpperCase();
		if (voucherRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
			throw new BusinessException("Mã voucher " + code + " đã tồn tại");
		}
		if (req.getQuantity() < v.getUsedCount()) {
			throw new BusinessException("Số lượng không được nhỏ hơn số lượt đã dùng (" + v.getUsedCount() + ")");
		}
		copy(req, v);
		return voucherRepository.save(v);
	}

	@Override
	@Transactional
	public void toggleActive(Integer id) {
		Voucher v = findById(id);
		v.setIsActive(!v.getIsActive());
	}

	@Override
	@Transactional
	public void delete(Integer id) {
		Voucher v = findById(id);
		if (v.getUsedCount() > 0) {
			v.setIsActive(false);
		} else {
			voucherRepository.delete(v);
		}
	}

	@Override
	public VoucherReq toReq(Voucher v) {
		VoucherReq req = new VoucherReq();
		req.setCode(v.getCode());
		req.setName(v.getName());
		req.setBranchId(v.getBranch() == null ? null : v.getBranch().getId());
		req.setApplyTo(VoucherApplyTo.PRODUCT);
		req.setDiscountType(v.getDiscountType());
		req.setDiscountValue(v.getDiscountValue());
		req.setMaxDiscount(v.getMaxDiscount());
		req.setMinOrderValue(v.getMinOrderValue());
		req.setQuantity(v.getQuantity());
		req.setStartDate(v.getStartDate());
		req.setEndDate(v.getEndDate());
		return req;
	}

	@Override
	public List<Voucher> findUsable(Integer branchId) {
		return voucherRepository.findUsable(branchId, LocalDateTime.now()).stream()
				.filter(v -> v.getApplyTo() == VoucherApplyTo.PRODUCT).toList();
	}

	@Override
	@Transactional(readOnly = true, noRollbackFor = BusinessException.class)
	public BigDecimal calculateDiscount(String code, Integer branchId, BigDecimal itemsTotal) {
		Voucher v = voucherRepository.findByCodeIgnoreCase(code.trim())
				.orElseThrow(() -> new BusinessException("Mã voucher không tồn tại"));
		LocalDateTime now = LocalDateTime.now();

		if (v.getApplyTo() != VoucherApplyTo.PRODUCT)
			throw new BusinessException("Hệ thống chỉ áp dụng mã giảm giá đồ uống");
		if (!v.getIsActive())
			throw new BusinessException("Voucher đã ngừng áp dụng");
		if (now.isBefore(v.getStartDate()))
			throw new BusinessException("Voucher chưa đến ngày áp dụng");
		if (now.isAfter(v.getEndDate()))
			throw new BusinessException("Voucher đã hết hạn");
		if (v.getUsedCount() >= v.getQuantity())
			throw new BusinessException("Voucher đã hết lượt sử dụng");
		if (v.getBranch() != null && !v.getBranch().getId().equals(branchId))
			throw new BusinessException("Mã này không áp dụng cho chi nhánh này");
		if (itemsTotal.compareTo(v.getMinOrderValue()) < 0)
			throw new BusinessException(
					"Đơn tối thiểu " + v.getMinOrderValue().toPlainString() + "đ mới dùng được voucher này");

		BigDecimal base = itemsTotal;

		BigDecimal discount;
		if (v.getDiscountType() == DiscountType.PERCENT) {
			discount = base.multiply(v.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
			if (v.getMaxDiscount() != null)
				discount = discount.min(v.getMaxDiscount());
		} else {
			discount = v.getDiscountValue();
		}
		return discount.min(base);
	}

	@Override
	@Transactional(readOnly = true, noRollbackFor = BusinessException.class)
	public BigDecimal calculateDiscount(String code, Integer branchId, Integer userId, PaymentMethod paymentMethod,
			BigDecimal itemsTotal) {
		BigDecimal discount = calculateDiscount(code, branchId, itemsTotal);
		Voucher v = voucherRepository.findByCodeIgnoreCase(code.trim())
				.orElseThrow(() -> new BusinessException("Mã voucher không tồn tại"));

		if (v.getPaymentMethod() != null && v.getPaymentMethod() != paymentMethod) {
			throw new BusinessException("Mã " + v.getCode() + " chỉ áp dụng khi thanh toán bằng "
					+ paymentLabel(v.getPaymentMethod()));
		}
		if (userId != null) checkUser(v, userId);
		return discount;
	}

	private void checkUser(Voucher v, Integer userId) {
		if (v.getPerUserLimit() != null) {
			long used = voucherUsageRepository.countByVoucherIdAndUserId(v.getId(), userId);
			if (used >= v.getPerUserLimit()) {
				throw new BusinessException(v.getPerUserLimit() == 1 ? "Bạn đã dùng mã " + v.getCode() + " rồi"
						: "Bạn đã dùng hết " + v.getPerUserLimit() + " lượt của mã " + v.getCode());
			}
		}
		VoucherAudience audience = v.getAudience() == null ? VoucherAudience.ALL : v.getAudience();
		if (audience == VoucherAudience.NEW_USER && !memberService.isNewCustomer(userId)) {
			throw new BusinessException("Mã " + v.getCode() + " chỉ dành cho đơn hàng đầu tiên của tài khoản mới");
		}
		if (audience == VoucherAudience.MIN_TIER && v.getMinTier() != null) {
			MemberTier current = memberService.getCurrentTier(userId);
			if (current.getMinSpent().compareTo(v.getMinTier().getMinSpent()) < 0) {
				throw new BusinessException("Mã " + v.getCode() + " chỉ dành cho hạng " + v.getMinTier().getName()
						+ " trở lên (bạn đang là hạng " + current.getName() + ")");
			}
		}
	}

	private String paymentLabel(PaymentMethod m) {
		return switch (m) {
			case COD -> "tiền mặt khi nhận hàng (COD)";
			case VNPAY -> "VNPAY";
			case MOMO -> "MoMo";
		};
	}

	@Override
	@Transactional
	public void markUsed(Integer voucherId) {
		findById(voucherId);
		if (voucherRepository.increaseUsedCount(voucherId) == 0) {
			throw new BusinessException("Voucher đã hết lượt sử dụng hoặc ngừng áp dụng");
		}
	}

	@Override
	@Transactional
	public void markUsed(Integer voucherId, Integer userId, Integer orderId) {
		Voucher v = findById(voucherId);
		if (v.getPerUserLimit() != null
				&& voucherUsageRepository.countByVoucherIdAndUserId(voucherId, userId) >= v.getPerUserLimit()) {
			throw new BusinessException("Bạn đã dùng hết lượt của mã " + v.getCode());
		}
		VoucherUsage usage = new VoucherUsage();
		usage.setVoucher(v);
		usage.setUser(userRepository.getReferenceById(userId));
		usage.setOrder(orderRepository.getReferenceById(orderId));
		voucherUsageRepository.save(usage);

		if (voucherRepository.increaseUsedCount(voucherId) == 0) {
			throw new BusinessException("Mã " + v.getCode() + " đã hết lượt sử dụng hoặc ngừng áp dụng");
		}
		voucherAlertService.checkRemaining(voucherId);
	}

	private void copy(VoucherReq req, Voucher v) {
		Branch branch = null;
		if (req.getBranchId() != null) {
			branch = branchRepository.findById(req.getBranchId())
					.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi nhánh #" + req.getBranchId()));
		}
		v.setCode(req.getCode().trim().toUpperCase());
		v.setName(req.getName().trim());
		v.setBranch(branch);
		v.setApplyTo(VoucherApplyTo.PRODUCT);
		v.setDiscountType(req.getDiscountType());
		v.setDiscountValue(req.getDiscountValue());
		v.setMaxDiscount(req.getDiscountType() == DiscountType.PERCENT ? req.getMaxDiscount() : null);
		v.setMinOrderValue(req.getMinOrderValue());
		v.setQuantity(req.getQuantity());
		v.setStartDate(req.getStartDate());
		v.setEndDate(req.getEndDate());
	}
}
