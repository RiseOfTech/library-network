package io.github.riseoftech.library.catalog.copy;

import io.github.riseoftech.library.common.ConflictException;

public class CopyInventoryNumberTakenException extends ConflictException {

    public static final String CODE = "COPY_INVENTORY_NUMBER_TAKEN";

    public CopyInventoryNumberTakenException(String inventoryNumber) {
        super(CODE, "Inventory number " + inventoryNumber + " is already taken");
    }
}
