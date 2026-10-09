package vn.utetra.service;

import vn.utetra.dto.request.VoucherReq;
import vn.utetra.entity.Voucher;

import java.math.BigDecimal;
import java.util.List;

public interface VoucherService {

	/** Voucher của app (Manager/Admin quản lý) */
	List<Voucher> findAppVouchers();

	/** Voucher của 1 shop (Vendor quản lý) */
	List<Voucher> findByBranch(Integer branchId);

	Voucher findById(Integer id);

	Voucher create(VoucherReq req);

	Voucher update(Integer id, VoucherReq req);

	/** Bật / tắt voucher */
	void toggleActive(Integer id);

	/** Xoá: voucher đã có người dùng thì chỉ tắt, không xoá */
	void delete(Integer id);

	VoucherReq toReq(Voucher v);

	/** Danh sách voucher khách chọn được khi đặt hàng ở shop branchId */
	List<Voucher> findUsable(Integer branchId);

	/**
	 * Kiểm tra voucher và tính số tiền được giảm.
	 * 
	 * @param itemsTotal  tổng tiền món của đơn
	 * @param shippingFee phí ship của đơn
	 * @return số tiền giảm (không vượt quá tiền món/phí ship)
	 * @throws vn.utetra.exception.BusinessException nếu voucher không dùng được
	 */
	BigDecimal calculateDiscount(String code, Integer branchId, BigDecimal itemsTotal, BigDecimal shippingFee);

	/** Gọi khi đặt hàng thành công: tăng used_count */
	void markUsed(Integer voucherId);
}