package vn.utetra.service;

import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.VendorBoardRes;
import vn.utetra.dto.response.VendorBranchRes;
import vn.utetra.dto.response.VendorHistoryRes;
import vn.utetra.dto.response.VendorOrderRes;
import vn.utetra.entity.enums.OrderStatus;

import java.util.List;

/** Quầy đơn hàng của Vendor/Barista: chỉ thấy và đổi được đơn của chi nhánh mình sở hữu */
public interface VendorOrderService {

	/** Các chi nhánh vendor sở hữu */
	List<VendorBranchRes> myBranches(Integer vendorId);

	/** branchId null -> chi nhánh đầu tiên của vendor; không phải của vendor -> ResourceNotFoundException */
	Integer resolveBranchId(Integer vendorId, Integer branchId);

	/** Đơn của chi nhánh chia theo cột */
	VendorBoardRes board(Integer vendorId, Integer branchId);

	/**
	 * Quán đổi trạng thái (1 click). Quán chỉ được:
	 * PENDING -> CONFIRMED (xác nhận) | CANCELLED (huỷ, bắt buộc lý do)
	 * Đơn đến lấy: CONFIRMED -> DELIVERED (khách đã nhận món)
	 * Đơn giao tận nơi: lấy hàng / đang giao / đã giao là việc của Shipper.
	 */
	void changeStatus(Integer vendorId, Integer orderId, OrderStatus to, String reason);

	/** Trang "Đơn hàng của shop": đơn theo 1 trong 7 trạng thái của đề, mới nhất lên đầu, 10 đơn / trang */
	VendorHistoryRes history(Integer vendorId, Integer branchId, OrderStatus status, int page);

	/** Chi tiết 1 đơn của chi nhánh mình (đơn chi nhánh khác -> ResourceNotFoundException) */
	OrderDetailRes orderDetail(Integer vendorId, Integer orderId);

	/** Thẻ đơn (có các nút quán được bấm) - dùng ở trang chi tiết */
	VendorOrderRes orderCard(Integer vendorId, Integer orderId);
}