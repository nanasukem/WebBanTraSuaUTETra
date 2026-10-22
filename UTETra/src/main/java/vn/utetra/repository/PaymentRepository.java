package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.Payment;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

	List<Payment> findByOrderIdOrderByCreatedAtDesc(Integer orderId);

	Payment findFirstByOrderIdOrderByCreatedAtDesc(Integer orderId);

	Optional<Payment> findByTransactionCode(String transactionCode);

	/** Tìm giao dịch theo mã mình gửi sang cổng (vnp_TxnRef / orderId MoMo) */
	Optional<Payment> findByTxnRef(String txnRef);

	List<Payment> findByOrderIdAndStatus(Integer orderId, String status);

	/** Giao dịch thành công mới nhất của đơn (để hoàn tiền / hiện mã giao dịch) */
	Optional<Payment> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(Integer orderId, Collection<String> statuses);

	/** Giao dịch còn chờ khách trả, tạo trước mốc thời gian (để hỏi lại cổng) */
	List<Payment> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);

	/** Giao dịch đang hoàn tiền (để thử hoàn lại nếu lần trước lỗi) */
	List<Payment> findByStatusAndRefundAttemptsLessThan(String status, Integer attempts);
}
