package vn.utetra.service;

import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.ManagerBoardRes;
import vn.utetra.dto.response.ManagerBranchRes;
import vn.utetra.dto.response.ManagerHistoryRes;
import vn.utetra.dto.response.ManagerOrderRes;
import vn.utetra.entity.enums.OrderStatus;

import java.util.List;

/** Quầy đơn hàng của Manager/Barista: chỉ thấy và đổi được đơn của chi nhánh mình sở hữu */
public interface ManagerOrderService {

	/** Các chi nhánh manager sở hữu */
	List<ManagerBranchRes> myBranches(Integer managerId);

	/** branchId null -> chi nhánh đầu tiên của manager; không phải của manager -> ResourceNotFoundException */
	Integer resolveBranchId(Integer managerId, Integer branchId);

	/** Đơn của chi nhánh chia theo cột */
	ManagerBoardRes board(Integer managerId, Integer branchId);

	/**
	 * Quán đổi trạng thái (1 click). Quán chỉ được:
	 * PENDING -> CONFIRMED (xác nhận) | CANCELLED (huỷ, bắt buộc lý do)
	 * Đơn đến lấy: CONFIRMED -> DELIVERED (khách đã nhận món)
	 * Đơn giao tận nơi: lấy hàng / đang giao / đã giao là việc của Shipper.
	 */
	void changeStatus(Integer managerId, Integer orderId, OrderStatus to, String reason);

	/** Trang "Đơn hàng của shop": đơn theo 1 trong 7 trạng thái của đề, mới nhất lên đầu, 10 đơn / trang */
	ManagerHistoryRes history(Integer managerId, Integer branchId, OrderStatus status, int page);

	/** Chi tiết 1 đơn của chi nhánh mình (đơn chi nhánh khác -> ResourceNotFoundException) */
	OrderDetailRes orderDetail(Integer managerId, Integer orderId);

	/** Thẻ đơn (có các nút quán được bấm) - dùng ở trang chi tiết */
	ManagerOrderRes orderCard(Integer managerId, Integer orderId);
}