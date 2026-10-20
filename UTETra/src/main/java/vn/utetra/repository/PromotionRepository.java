package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import vn.utetra.entity.Promotion;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Integer> {

	boolean existsByCodeIgnoreCase(String code);

	boolean existsByCodeIgnoreCaseAndIdNot(String code, Integer id);
}