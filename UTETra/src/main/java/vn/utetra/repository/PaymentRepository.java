package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

	List<Payment> findByOrderIdOrderByCreatedAtDesc(Integer orderId);

	Payment findFirstByOrderIdOrderByCreatedAtDesc(Integer orderId);

	Optional<Payment> findByTransactionCode(String transactionCode);
}
