package vn.utetra.service;

import vn.utetra.dto.response.OrderDetailRes;
import vn.utetra.dto.response.ManagerBoardRes;
import vn.utetra.dto.response.ManagerBranchRes;
import vn.utetra.dto.response.ManagerHistoryRes;
import vn.utetra.dto.response.ManagerOrderRes;
import vn.utetra.entity.enums.OrderStatus;

import java.util.List;

public interface ManagerOrderService {

	List<ManagerBranchRes> myBranches(Integer managerId);

	Integer resolveBranchId(Integer managerId, Integer branchId);

	ManagerBoardRes board(Integer managerId, Integer branchId);

	void changeStatus(Integer managerId, Integer orderId, OrderStatus to, String reason);

	ManagerHistoryRes history(Integer managerId, Integer branchId, String tab, int page);

	OrderDetailRes orderDetail(Integer managerId, Integer orderId);

	ManagerOrderRes orderCard(Integer managerId, Integer orderId);
}