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

	Optional<Payment> findByTxnRef(String txnRef);

	boolean existsByTxnRef(String txnRef);

	List<Payment> findByOrderIdAndStatus(Integer orderId, String status);

	Optional<Payment> findFirstByOrderIdAndStatusInOrderByCreatedAtDesc(Integer orderId, Collection<String> statuses);

	List<Payment> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);

	List<Payment> findByStatusAndRefundAttemptsLessThan(String status, Integer attempts);
}
