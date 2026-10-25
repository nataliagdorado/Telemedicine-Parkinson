//POJO COMPARTIDO --> SE TRANSMITE POR LA RED TCP/IP ENTRE CLIENTE PACIENTE Y EL SERVIDOR
//Implementan Serializable para ser convertidos a JSON sin problemas

//Agrupa y empaqueta las lecturas dek BITalino
//requisito R3

package common;

import java.io.Serializable;
import java.util.List;

public class SampleBlock implements Serializable {
    private static final long serialVersionUID=1L;

    private String sessionId;
    private List<Integer> emgSamples;  // Actividad muscular
    private List<Integer> accX;        // Acelerómetro Eje X
    private List<Integer> accY;        // Acelerómetro Eje Y
    private List<Integer> accZ;        // Acelerómetro Eje Z

    public SampleBlock(){}

    public SampleBlock(String sessionId, List<Integer> emgSamples, List<Integer> accX, List<Integer> accY, List<Integer> accZ) {
        this.sessionId = sessionId;
        this.emgSamples = emgSamples;
        this.accX = accX;
        this.accY = accY;
        this.accZ = accZ;
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<Integer> getEmgSamples() {
        return emgSamples;
    }

    public List<Integer> getAccX() {
        return accX;
    }

    public List<Integer> getAccY() {
        return accY;
    }

    public List<Integer> getAccZ() {
        return accZ;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setEmgSamples(List<Integer> emgSamples) {
        this.emgSamples = emgSamples;
    }

    public void setAccX(List<Integer> accX) {
        this.accX = accX;
    }

    public void setAccY(List<Integer> accY) {
        this.accY = accY;
    }

    public void setAccZ(List<Integer> accZ) {
        this.accZ = accZ;
    }

    @Override
    public String toString() {
        return "SampleBlock{" +
                "sessionId='" + sessionId + '\'' +
                ", emgSamples=" + emgSamples +
                ", accX=" + accX +
                ", accY=" + accY +
                ", accZ=" + accZ +
                '}';
    }
}