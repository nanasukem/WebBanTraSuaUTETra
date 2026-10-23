package vn.utetra.service;

import vn.utetra.dto.request.VoucherReq;
import vn.utetra.entity.Voucher;
import vn.utetra.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

public interface VoucherService {

	/** Voucher của app (Admin quản lý) */
	List<Voucher> findAppVouchers();

	/** Voucher riêng của 1 chi nhánh (Admin tạo) */
	List<Voucher> findByBranch(Integer branchId);

	/** Admin: tất cả voucher (toàn chuỗi + riêng từng chi nhánh) */
	List<Voucher> findAllVouchers();

	/**
	 * Manager CHỈ XEM: các voucher áp dụng tại chi nhánh (toàn chuỗi + riêng chi nhánh),
	 * kèm trạng thái và số lượt khách đã dùng / số tiền đã giảm tại chi nhánh. Đang chạy lên đầu.
	 */
	List<vn.utetra.dto.response.ManagerVoucherRes> branchView(Integer branchId);

	Voucher findById(Integer id);

	Voucher create(VoucherReq req);

	Voucher update(Integer id, VoucherReq req);

	/** Bật / tắt voucher */
	void toggleActive(Integer id);

	/** Xoá: voucher đã có người dùng thì chỉ tắt, không xoá */
	void delete(Integer id);

	VoucherReq toReq(Voucher v);

	/** Danh sách voucher khách chọn được khi đặt hàng ở chi nhánh branchId */
	List<Voucher> findUsable(Integer branchId);

	/**
	 * Kiểm tra voucher và tính số tiền được giảm (kiểm tra chung, không xét người dùng).
	 *
	 * @param itemsTotal  tổng tiền món của đơn
	 * @return số tiền giảm (không vượt quá tiền món)
	 * @throws vn.utetra.exception.BusinessException nếu voucher không dùng được
	 */
	BigDecimal calculateDiscount(String code, Integer branchId, BigDecimal itemsTotal);

	/** Gọi khi đặt hàng thành công: tăng used_count (bản cũ, không ghi lịch sử) */
	void markUsed(Integer voucherId);

	// ===== v2 + v3: kiểm tra đầy đủ khi đặt hàng =====

	/**
	 * Như hàm trên + phương thức thanh toán (v3) + kiểm tra theo người dùng (v2):
	 * lượt mỗi người, tài khoản mới, hạng tối thiểu.
	 */
	BigDecimal calculateDiscount(String code, Integer branchId, Integer userId, PaymentMethod paymentMethod,
			BigDecimal itemsTotal);

	/** Đặt hàng thành công: kiểm tra lượt mỗi người + ghi lịch sử dùng mã + tăng used_count */
	void markUsed(Integer voucherId, Integer userId, Integer orderId);
}