//pedir por consola los sintomas al paciente y comprobar la validez de los datos recibidos

package patient;

import common.Message;
import common.SymptomCatalog;
import common.SymptomReport;
import common.SymptomValidator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class SymptomFormController {
    private static final String CANCEL_WORD="cancelar";
    private static final DateTimeFormatter HOUR_FORMAT=DateTimeFormatter.ofPattern("H:mm");

    //El Scanner se recibe desde Patient App y solo debe haber uno sobre System.in en toda la app
    private final Scanner scanner;

    //SE lanza cuando el paciente escribe cancelar o se cierra la entrada estandar
    private static class FromCancelledException extends Exception{
        FromCancelledException(){
            super("Cancelled form");
        }
    }
    public SymptomFormController(Scanner scanner) {
        this.scanner = scanner;
    }
    public SymptomReport askForReport(String patientId){
        System.out.println();
        System.out.println("---------SYMPTOM REPORT---------(write \""+CANCEL_WORD+"\"for exit)");
        printScale();

        try{
            while (true){ //El formulario sera repetido indefinidamente por si hay algun tipo de error solucionable
                int rigidity=askLevel("RIGIDITY level");
                int tremor= askLevel("TREMOR level");
                boolean medicationTaken= askYesNo("¿Have you taken the medication today?(y/n):");
                LocalDateTime lastDose=null;
                if(medicationTaken){
                    lastDose=askLastDoseTime();
                }
                boolean freezing = askYesNo("¿Did you have any recent freezing episodes?(y/n):");
                String notes = askNotes();
                //El report id se deja a null para que lo asigne el servidor
                SymptomReport report=new SymptomReport(patientId,null,rigidity,tremor,medicationTaken,lastDose,freezing,notes);

                List<String>errors= SymptomValidator.validate(report);//validate revisa el report entero y devuelve una lista con los errores escritos como frases, si no hay ninguno, devuelve una lista vacía
                //Hay reglas que dependen de varios campos a la vez por lo que no vale con la validación individual de los datos
                //Si hay errores, mostrarlos y volverlos a empezar:

                if(!errors.isEmpty()){
                    System.out.println("The report has errors: ");
                    for(String error: errors){
                        System.out.println(" -"+error);
                    }
                    System.out.println("Let's fill up the form again.\n");
                    continue;
                }

                printSummary(report);
                if(askYesNo("¿Send this report? (y/n): ")){
                    return report;
                }
                System.out.println("Alright, let's fill it again.\n");
            }
        }catch (FromCancelledException e){
            System.out.println("Cancelled report. Nothing has been sent.");
            return null;
        }
    }


    //Convierte el reporte en el mensaje Symptom Report del protocolo
    public static Message buildMessage(SymptomReport report, String requestId, String sessionToken) {
        Message msg = new Message("SYMPTOM_REPORT", requestId);
        msg.setPatientId(report.getPatientId());
        msg.addData("sessionToken", sessionToken);
        msg.addData("timeStamp", report.getTimeStamp().toString());
        msg.addData("rigidityLevel", report.getRigidityLevel());
        msg.addData("tremorLevel", report.getTremorLevel());
        msg.addData("medicationTaken", report.isMedicationTaken());
        msg.addData("lastDoseTime", report.getLastDoseTime() == null ? null : report.getLastDoseTime().toString());
        msg.addData("freezingEpisodes", report.isFreezingEpisodes());
        msg.addData("notes", report.getNotes());
        return msg;
    }

    //preguntas individuales--> cada una se repite hasta recibir algun valor válido

    //SE lee una linea y se lanza FormCancelledException si escribe "Cancellar" o se cierra la entreda

    private String readLine(String prompt) throws FromCancelledException{
        System.out.print(prompt);
        if(!scanner.hasNextLine()){ //entrada cerrada (Ctrl+D o fin de fichero)
            throw new FromCancelledException();
        }
        String line=scanner.nextLine().trim();
        if(line.equalsIgnoreCase(CANCEL_WORD)){
            throw new FromCancelledException();
        }
        return line;
    }

    //Pide un nivel entero entre MIN_LEVEL y MAX_LEVEL (escala MDS-UPDRS 0-4)
    private int askLevel(String name) throws FromCancelledException{
        String prompt=name+" ("+SymptomCatalog.MIN_LEVEL+"-"+SymptomCatalog.MAX_LEVEL+"): ";
        while(true){
            String line=readLine(prompt);
            try{
                int value=Integer.parseInt(line);
                if(value>=SymptomCatalog.MIN_LEVEL && value<=SymptomCatalog.MAX_LEVEL){
                    System.out.println("   -> "+SymptomCatalog.labelFor(value));
                    return value;
                }
                System.out.println("   It must be between "+SymptomCatalog.MIN_LEVEL+" and "+SymptomCatalog.MAX_LEVEL+".");
            }catch (NumberFormatException e){ //el paciente ha escrito algo que no es un numero
                System.out.println("   \""+line+"\" is not a number. Please write an integer.");
            }
        }
    }

    //Pregunta de si/no: acepta y, yes / n, no
    private boolean askYesNo(String prompt) throws FromCancelledException{
        while(true){
            String line=readLine(prompt).toLowerCase();
            switch(line){
                case "y": case "yes":
                    return true;
                case "n": case "no":
                    return false;
                default:
                    System.out.println("   Please answer \"y\" or \"n\".");
            }
        }
    }

    //Pide la hora de la ultima dosis en formato HH:mm (se entiende que es de hoy)
    //"now" = la hora actual. Si la hora es posterior a la actual, se toma como de ayer
    //(ej. a las 00:30 el paciente dice que tomo la dosis a las 23:00)
    private LocalDateTime askLastDoseTime() throws FromCancelledException{
        while(true){
            String line=readLine("At what time did you take your last dose? (HH:mm or \"now\"): ");
            if(line.equalsIgnoreCase("now")){
                return LocalDateTime.now().withSecond(0).withNano(0);
            }
            try{
                LocalTime time=LocalTime.parse(line, HOUR_FORMAT);
                LocalDateTime dose=LocalDateTime.of(LocalDate.now(), time);
                if(dose.isAfter(LocalDateTime.now())){
                    dose=dose.minusDays(1);
                    System.out.println("   (Understood as yesterday at "+time+")");
                }
                return dose;
            }catch (DateTimeParseException e){ //formato de hora no valido
                System.out.println("   Invalid format. Example: 8:30 or 14:05.");
            }
        }
    }

    //Notas libres opcionales (Enter para dejarlas vacias), con longitud maxima
    private String askNotes() throws FromCancelledException{
        while(true){
            String line=readLine("Notes (optional, press Enter to skip): ");
            if(line.isEmpty()){
                return null;
            }
            if(line.length()<=SymptomCatalog.MAX_NOTES_LENGTH){
                return line;
            }
            System.out.println("   Maximum "+SymptomCatalog.MAX_NOTES_LENGTH+" characters (you wrote "+line.length()+").");
        }
    }

    //Salida por pantalla

    //Muestra la escala de niveles antes de empezar el formulario
    private void printScale(){
        System.out.print("Scale: ");
        for(int i=SymptomCatalog.MIN_LEVEL; i<=SymptomCatalog.MAX_LEVEL; i++){
            System.out.print(i+"="+SymptomCatalog.labelFor(i));
            if(i<SymptomCatalog.MAX_LEVEL){
                System.out.print(", ");
            }
        }
        System.out.println();
    }

    //Muestra todo lo que ha introducido el paciente para que lo revise antes de enviarlo
    private void printSummary(SymptomReport r){
        System.out.println();
        System.out.println("---------SUMMARY---------");
        System.out.println("Rigidity:    "+r.getRigidityLevel()+" ("+ SymptomCatalog.labelFor(r.getRigidityLevel())+")");
        System.out.println("Tremor:      "+r.getTremorLevel()+" ("+SymptomCatalog.labelFor(r.getTremorLevel())+")");
        if(r.isMedicationTaken()){
            System.out.println("Medication:  yes, "+r.getMinutesSinceLastDose()+" min ago");
        }else{
            System.out.println("Medication:  no");
        }
        if(r.isFreezingEpisodes()){
            System.out.println("Freezing:    yes");
        }else{
            System.out.println("Freezing:    no");
        }
        if(r.getNotes()==null){
            System.out.println("Notes:       -");
        }else{
            System.out.println("Notes:       "+r.getNotes());
        }
        System.out.println("-------------------------");
    }



}