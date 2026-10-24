package vn.utetra.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.utetra.entity.Carrier;

import java.util.List;

public interface CarrierRepository extends JpaRepository<Carrier, Integer> {

	boolean existsByNameIgnoreCase(String name);

	boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

	List<Carrier> findByNameContainingIgnoreCaseOrderByIdAsc(String keyword);

	List<Carrier> findByIsActiveTrueOrderByFeeAsc();
}