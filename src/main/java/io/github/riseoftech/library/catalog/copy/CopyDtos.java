package io.github.riseoftech.library.catalog.copy;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class CopyDtos {

    private CopyDtos() {
    }

    public record RegisterCopyRequest(
            @NotNull Long bookId,
            @NotNull Long branchId,
            @NotNull @Pattern(regexp = "[A-Z0-9-]{3,30}",
                    message = "must be 3-30 uppercase letters, digits or hyphens") String inventoryNumber) {
    }

    public record CopyResponse(long id, long bookId, long branchId, String inventoryNumber, CopyStatus status) {

        static CopyResponse from(Copy copy) {
            return new CopyResponse(copy.getId(), copy.getBook().getId(), copy.getBranch().getId(),
                    copy.getInventoryNumber(), copy.getStatus());
        }
    }
}
