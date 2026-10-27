package patient.bitalino;

// import static sun.jvm.hotspot.interpreter.Bytecodes.name; ESTA LINEA DA ERROR @sarah

public enum BITalinoErrorTypes {
    BT_DEVICE_NOT_CONNECTED(0, "The Bluetooth device is not connected."),
    PORT_COULD_NOT_BE_OPENED(1, "The port could not be opened."),
    DEVICE_NOT_INITIALIZED(2, "The device is not initialized."),
    DEVICE_NOT_IDLE(3, "The device is not idle."),
    DEVICE_NOT_IN_ACQUISITION_MODE(4, "The device is not in acquisition mode."),
    INVALID_PARAMETER(5, "Invalid parameter."),
    UNDEFINED_ERROR(6, "Undefined error.");

    private final int value;
    private final String description;

    BITalinoErrorTypes(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return name() + " (" + value + "): " + description;
    }
}
