package vn.utetra.service;

import vn.utetra.dto.request.CarrierReq;
import vn.utetra.entity.Carrier;

import java.util.List;

public interface CarrierService {

	/** Danh sách + tìm theo tên. keyword null/rỗng = lấy tất cả */
	List<Carrier> search(String keyword);

	/** Nhà vận chuyển đang hoạt động (khách chọn ở trang checkout) */
	List<Carrier> findActive();

	Carrier findById(Integer id);

	Carrier create(CarrierReq req);

	Carrier update(Integer id, CarrierReq req);

	/** Bật / tắt. Không xoá vì đơn hàng cũ đang tham chiếu nhà vận chuyển */
	void toggleActive(Integer id);

	CarrierReq toReq(Carrier c);
}