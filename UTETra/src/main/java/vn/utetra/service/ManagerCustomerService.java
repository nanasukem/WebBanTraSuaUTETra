package vn.utetra.service;

import vn.utetra.dto.response.ManagerCustomerDetailRes;
import vn.utetra.dto.response.ManagerCustomerRes;

import java.util.List;

/**
 * (Chức năng mở rộng) Manager kiểm tra khách hàng của chi nhánh mình.
 * Khách của chi nhánh = người đã từng đặt đơn ở chi nhánh đó. Chỉ xem: không sửa, không khoá tài khoản khách
 * (quản lý tài khoản là việc của Admin).
 */
public interface ManagerCustomerService {

	/**
	 * Danh sách khách đã đặt đơn ở chi nhánh (lần đặt gần nhất lên đầu).
	 * keyword: lọc theo họ tên, tên đăng nhập hoặc số điện thoại (bỏ trống = tất cả).
	 * branchId không phải của manager -> ResourceNotFoundException.
	 */
	List<ManagerCustomerRes> customers(Integer managerId, Integer branchId, String keyword);

	/** Chi tiết 1 khách + các đơn của khách ở chi nhánh; khách chưa từng đặt ở chi nhánh -> ResourceNotFoundException */
	ManagerCustomerDetailRes customer(Integer managerId, Integer branchId, Integer userId);
}
