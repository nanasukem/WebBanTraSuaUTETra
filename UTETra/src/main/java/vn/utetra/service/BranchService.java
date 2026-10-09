package vn.utetra.service;

import vn.utetra.dto.request.BranchReq;
import vn.utetra.entity.Branch;
import vn.utetra.entity.User;
import vn.utetra.entity.enums.BranchStatus;

import java.util.List;

public interface BranchService {

	/** Tìm kiếm + lọc. keyword/status null = lấy tất cả */
	List<Branch> search(String keyword, BranchStatus status);

	/** Các shop đang hoạt động (hiển thị cho khách) */
	List<Branch> findApproved();

	Branch findById(Integer id);

	Branch create(BranchReq req);

	Branch update(Integer id, BranchReq req);

	/** Duyệt / từ chối / tạm ngưng / mở lại */
	void changeStatus(Integer id, BranchStatus newStatus);

	/** Chuyển entity -> form để hiển thị trang Sửa */
	BranchReq toReq(Branch branch);

	/** Danh sách tài khoản VENDOR để chọn chủ shop */
	List<User> findVendors();
}