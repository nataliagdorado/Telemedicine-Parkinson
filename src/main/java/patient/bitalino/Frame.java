package patient.bitalino;

public class Frame {
    public int seq;
    public int[] digital = new int[4];
    public int[] analog = new int[6];

    public Frame() {
    }
    @Override
    public String toString() {
        return "Frame{" +
                "seq=" + seq +
                ", digital=" + digital[0] + "," + digital[1] + "," + digital[2] + "," + digital[3] +
                ", analog=" + analog[0] + "," + analog[1] + "," + analog[2] + "," + analog[3] + "," + analog[4] + "," + analog[5] +
                '}';
    }

}
