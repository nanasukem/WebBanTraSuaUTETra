package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.MemberTier;

import java.util.List;

public interface MemberTierRepository extends JpaRepository<MemberTier, Integer> {

	/** Các hạng từ thấp đến cao */
	List<MemberTier> findAllByOrderByMinSpentAsc();
}