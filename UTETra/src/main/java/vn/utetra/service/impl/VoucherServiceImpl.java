package vn.utetra.service.impl;

import vn.utetra.dto.request.VoucherReq;
import vn.utetra.entity.Branch;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.DiscountType;
import vn.utetra.entity.enums.VoucherApplyTo;
import vn.utetra.exception.BusinessException;
import vn.utetra.exception.ResourceNotFoundException;
import vn.utetra.repository.BranchRepository;
import vn.utetra.repository.VoucherRepository;
import vn.utetra.service.VoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class VoucherServiceImpl implements VoucherService {

	private final VoucherRepository voucherRepository;
	private final BranchRepository branchRepository;

	public VoucherServiceImpl(VoucherRepository voucherRepository, BranchRepository branchRepository) {
		this.voucherRepository = voucherRepository;
		this.branchRepository = branchRepository;
	}

	@Override
	public List<Voucher> findAppVouchers() {
		return voucherRepository.findByBranchIsNullOrderByCreatedAtDesc();
	}

	@Override
	public List<Voucher> findByBranch(Integer branchId) {
		return voucherRepository.findByBranch_IdOrderByCreatedAtDesc(branchId);
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
			v.setIsActive(false); // đã gắn với đơn hàng -> không xoá được, chỉ tắt
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
		req.setApplyTo(v.getApplyTo());
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
		return voucherRepository.findUsable(branchId, LocalDateTime.now());
	}

	@Override
	public BigDecimal calculateDiscount(String code, Integer branchId, BigDecimal itemsTotal, BigDecimal shippingFee) {
		Voucher v = voucherRepository.findByCodeIgnoreCase(code.trim())
				.orElseThrow(() -> new BusinessException("Mã voucher không tồn tại"));
		LocalDateTime now = LocalDateTime.now();

		if (!v.getIsActive())
			throw new BusinessException("Voucher đã ngừng áp dụng");
		if (now.isBefore(v.getStartDate()))
			throw new BusinessException("Voucher chưa đến ngày áp dụng");
		if (now.isAfter(v.getEndDate()))
			throw new BusinessException("Voucher đã hết hạn");
		if (v.getUsedCount() >= v.getQuantity())
			throw new BusinessException("Voucher đã hết lượt sử dụng");
		if (v.getBranch() != null && !v.getBranch().getId().equals(branchId))
			throw new BusinessException("Voucher không áp dụng cho shop này");
		if (itemsTotal.compareTo(v.getMinOrderValue()) < 0)
			throw new BusinessException(
					"Đơn tối thiểu " + v.getMinOrderValue().toPlainString() + "đ mới dùng được voucher này");

		// Giảm trên tiền món hay trên phí ship
		BigDecimal base = v.getApplyTo() == VoucherApplyTo.PRODUCT ? itemsTotal : shippingFee;

		BigDecimal discount;
		if (v.getDiscountType() == DiscountType.PERCENT) {
			discount = base.multiply(v.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
			if (v.getMaxDiscount() != null)
				discount = discount.min(v.getMaxDiscount());
		} else {
			discount = v.getDiscountValue();
		}
		return discount.min(base); // không giảm quá số tiền gốc
	}

	@Override
	@Transactional
	public void markUsed(Integer voucherId) {
		Voucher v = findById(voucherId);
		if (v.getUsedCount() >= v.getQuantity()) {
			throw new BusinessException("Voucher đã hết lượt sử dụng");
		}
		v.setUsedCount(v.getUsedCount() + 1);
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
		v.setApplyTo(req.getApplyTo());
		v.setDiscountType(req.getDiscountType());
		v.setDiscountValue(req.getDiscountValue());
		// max_discount chỉ có ý nghĩa với PERCENT
		v.setMaxDiscount(req.getDiscountType() == DiscountType.PERCENT ? req.getMaxDiscount() : null);
		v.setMinOrderValue(req.getMinOrderValue());
		v.setQuantity(req.getQuantity());
		v.setStartDate(req.getStartDate());
		v.setEndDate(req.getEndDate());
	}
}