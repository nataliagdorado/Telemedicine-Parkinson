//Interfaz de adquisicion de señales para alternar entre la placa real (BITalino) y la simulacion
//Para demostrar la funcionalidad multihilo--> utilizamos 1 placa real y el resto de conexiones las simulamos
//Interfaz-->Abstraccion-->eL controlador de sesion no necesita saber si esta recibiendo datos del simulador o de la placa

package patient;
import common.SampleBlock;

public interface SignalSource {
    boolean connect(String address, int samplingRate);//Establecimiento de la comunicaicon inicial con la fuente de la señal
    //Direccion MAC de la placa (o una falsa) + freq de muestreo--> devuelve true si la conexion fue exitosa
    boolean startAcquisition(int[]channels);//comando de inciio de captura-->true si se inicio la grabacion correctamente
    SampleBlock readBlock(int numberOfSamples, String sessionId);//lectura rafaga/bloque de muestras del buffer de entrada
    //los parametros son la cantidad de muestras a extraer y el ID de la sesion para empaquetarlo en el POJO
    //devuelve las muestras leidas para enviar o procesar
    void stopAcquisition();//detener flujo de envio de datos pero mentiene abierta la conexion bluetooth para reutilizarla si fuera necesario

    void disconnect();

    boolean isConnected();//consulta estado iinterno
    boolean isRecording();//consulta estado interno
}