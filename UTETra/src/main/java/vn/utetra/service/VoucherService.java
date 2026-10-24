package vn.utetra.service;

import vn.utetra.dto.request.VoucherReq;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

public interface VoucherService {

	List<Voucher> findAppVouchers();

	List<Voucher> findByBranch(Integer branchId);

	List<Voucher> findAllVouchers();

	List<vn.utetra.dto.response.ManagerVoucherRes> branchView(Integer branchId);

	Voucher findById(Integer id);

	Voucher create(VoucherReq req);

	Voucher update(Integer id, VoucherReq req);

	void toggleActive(Integer id);

	void delete(Integer id);

	VoucherReq toReq(Voucher v);

	List<Voucher> findUsable(Integer branchId);

	BigDecimal calculateDiscount(String code, Integer branchId, BigDecimal itemsTotal);

	void markUsed(Integer voucherId);

	BigDecimal calculateDiscount(String code, Integer branchId, Integer userId, PaymentMethod paymentMethod,
			BigDecimal itemsTotal);

	void markUsed(Integer voucherId, Integer userId, Integer orderId);
}