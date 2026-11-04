package io.github.riseoftech.library.catalog.branch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BranchDtos {

    private BranchDtos() {
    }

    public record CreateBranchRequest(
            @NotNull @Pattern(regexp = "[A-Z0-9-]{2,20}",
                    message = "must be 2-20 uppercase letters, digits or hyphens") String code,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 500) String address) {
    }

    public record UpdateBranchRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 500) String address) {
    }

    public record BranchResponse(long id, String code, String name, String address) {

        static BranchResponse from(Branch branch) {
            return new BranchResponse(branch.getId(), branch.getCode(), branch.getName(), branch.getAddress());
        }
    }
}
