//POJOS QUE REPRESENTAN LA INFO QUE GUARDA EL SERVIDOR (FICHEROS O BASE DE DATOS)

//Registro completo guardado en el Disco o Base de Datos de una prueba realizada por un paciente, uniendo los metadatos
//de la sesion, las muestras del bitalino y el reporte de sintomas


package server.data;

import common.SampleBlock;
import common.SessionInfo;
import common.SymptomReport;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MonitoringSession implements Serializable {
    private static final long serialVersionUID=1L;

    private SessionInfo info;
    private SymptomReport symptomReport;
    private List<SampleBlock>signalBlocks;

    public MonitoringSession(){
        this.signalBlocks=new ArrayList<>();
    }

    public MonitoringSession(SessionInfo info, SymptomReport symptomReport) {
        this.info = info;
        this.symptomReport = symptomReport;
        this.signalBlocks = new ArrayList<>();
    }
    public void addSampleBlock(SampleBlock block){
        this.signalBlocks.add(block);
    }

    public SessionInfo getInfo() {
        return info;
    }

    public SymptomReport getSymptomReport() {
        return symptomReport;
    }

    public List<SampleBlock> getSignalBlocks() {
        return signalBlocks;
    }

    public void setInfo(SessionInfo info) {
        this.info = info;
    }

    public void setSymptomReport(SymptomReport symptomReport) {
        this.symptomReport = symptomReport;
    }

    public void setSignalBlocks(List<SampleBlock> signalBlocks) {
        this.signalBlocks = signalBlocks;
    }
}