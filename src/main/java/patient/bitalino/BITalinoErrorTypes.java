package patient.bitalino;


public enum BITalinoErrorTypes {
    //Cada cte llama al constructor con un value y uan descripción del error
    BT_DEVICE_NOT_CONNECTED(0, "The Bluetooth device is not connected."),
    PORT_COULD_NOT_BE_OPENED(1, "The port could not be opened."),
    DEVICE_NOT_INITIALIZED(2, "The device is not initialized."),
    DEVICE_NOT_IDLE(3, "The device is not idle."),
    DEVICE_NOT_IN_ACQUISITION_MODE(4, "The device is not in acquisition mode."),
    INVALID_PARAMETER(5, "Invalid parameter."),
    UNDEFINED_ERROR(6, "Undefined error."),
    LOST_COMUNICATION(7,"Lost communication with the device (powered off or out of range)."),
    //cuando se pierda el bluetooth o la placa este fuera del alcance
    INVALID_DATA(8,"Corrupted data recieved from the device (CRC errors)."),
    //cuando lleguen tramas corruptas detectadas por el CRC
    READ_TIMEOUT(9,"No data recieved from the device within the configured time");
    //paso el tiempo maximo establecido sin que se reciban datos

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
