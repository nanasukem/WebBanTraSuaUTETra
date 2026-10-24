package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.VoucherUsage;

import java.util.List;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Integer> {

	long countByVoucherIdAndUserId(Integer voucherId, Integer userId);

	List<VoucherUsage> findByOrderId(Integer orderId);

}