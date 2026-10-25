package patient.bitalino;

public class BITalinoException extends Exception{
    private static final long serialVersionUID = 1L;
    private final BITalinoErrorTypes type;

    public BITalinoException(BITalinoErrorTypes type) {
        super(type.getDescription());
        this.type = type;
    }

    public BITalinoErrorTypes getType() {
        return type;
    }
}
