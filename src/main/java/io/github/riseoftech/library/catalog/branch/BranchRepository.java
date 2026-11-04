package io.github.riseoftech.library.catalog.branch;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    boolean existsByCode(String code);

    List<Branch> findAllByOrderByCodeAsc();
}
