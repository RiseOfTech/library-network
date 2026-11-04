package io.github.riseoftech.library.catalog.branch;

import java.util.List;

import io.github.riseoftech.library.catalog.branch.BranchDtos.BranchResponse;
import io.github.riseoftech.library.catalog.branch.BranchDtos.CreateBranchRequest;
import io.github.riseoftech.library.catalog.branch.BranchDtos.UpdateBranchRequest;
import io.github.riseoftech.library.common.UniqueConstraints;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BranchService {

    static final String CODE_CONSTRAINT = "uk_branch_code";

    private final BranchRepository branches;

    public BranchService(BranchRepository branches) {
        this.branches = branches;
    }

    @Transactional
    public BranchResponse create(CreateBranchRequest request) {
        if (branches.existsByCode(request.code())) {
            throw new BranchCodeTakenException(request.code());
        }
        try {
            Branch branch = branches.saveAndFlush(new Branch(request.code(), request.name(), request.address()));
            return BranchResponse.from(branch);
        } catch (DataIntegrityViolationException ex) {
            if (UniqueConstraints.isViolated(ex, CODE_CONSTRAINT)) {
                throw new BranchCodeTakenException(request.code());
            }
            throw ex;
        }
    }

    @Transactional
    public BranchResponse update(long id, UpdateBranchRequest request) {
        Branch branch = load(id);
        branch.update(request.name(), request.address());
        return BranchResponse.from(branch);
    }

    public BranchResponse get(long id) {
        return BranchResponse.from(load(id));
    }

    public List<BranchResponse> list() {
        return branches.findAllByOrderByCodeAsc().stream().map(BranchResponse::from).toList();
    }

    private Branch load(long id) {
        return branches.findById(id).orElseThrow(() -> new BranchNotFoundException(id));
    }
}
