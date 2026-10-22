package vn.utetra.service;

import vn.utetra.dto.request.BranchReq;
import vn.utetra.dto.response.NearbyBranchRes;
import vn.utetra.entity.Branch;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.BranchStatus;

import java.util.List;

public interface BranchService {

	/** Tìm theo tên/địa chỉ + lọc trạng thái (trang Admin) */
	List<Branch> search(String keyword, BranchStatus status);

	/** Chi nhánh đang hoạt động (khách được chọn) */
	List<Branch> findApproved();

	Branch findById(Integer id);

	Branch create(BranchReq req);

	Branch update(Integer id, BranchReq req);

	/** Đổi trạng thái theo đúng luồng */
	void changeStatus(Integer id, BranchStatus newStatus);

	BranchReq toReq(Branch branch);

	/** Danh sách tài khoản Manager (chọn người quản lý chi nhánh) */
	List<User> findManagers();

	/**
	 * Chi nhánh đang hoạt động, sắp xếp từ gần đến xa vị trí (lat, lng) của khách.
	 * lat/lng = null -> vẫn trả đủ danh sách, không có khoảng cách.
	 */
	List<NearbyBranchRes> findNearby(Double lat, Double lng);
	

	/** username có phải chủ (Manager) của chi nhánh branchId không. Chi nhánh không tồn tại -> false */
	boolean isOwner(Integer branchId, String username);
}