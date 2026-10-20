package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.VoucherUsage;

import java.util.List;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Integer> {

	/** Người này đã dùng mã này bao nhiêu lần */
	long countByVoucherIdAndUserId(Integer voucherId, Integer userId);

	/** Các mã đã dùng cho 1 đơn (hoàn lượt khi huỷ đơn) */
	List<VoucherUsage> findByOrderId(Integer orderId);

}