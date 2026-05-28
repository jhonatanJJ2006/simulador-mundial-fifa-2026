package ec.edu.utpl.computacion.proava.mundialsimulador.concurrencia;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.GeneradorEnfrentamientos;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.SimuladorPartido;

import java.util.concurrent.Callable;

/**
 * Envuelve la simulación de UN partido como una tarea paralelizable.
 *
 * Implementa Callable<ResultadoPartido>, no Runnable, porque necesitamos
 * RETORNAR el resultado del partido. Runnable solo serviría si el partido
 * "hiciera algo" sin devolver datos (ej. enviar un email).
 *
 * Esta clase es INMUTABLE (campos final, sin setters): puede ser
 * construida en el hilo principal y ejecutada en otro hilo sin ningún
 * riesgo de concurrencia.
 *
 * NO ESCRIBE A LA BD: solo retorna el resultado. La persistencia la hace
 * el hilo coordinador DESPUÉS de recolectar todos los resultados. Esta
 * separación es FUNDAMENTAL: evita que múltiples hilos compitan por
 * conexiones JDBC, simplifica el manejo de errores y mantiene la lógica
 * concurrente limpia.
 */

public class SimuladorPartidoCallable implements Callable<ResultadoPartido> {
    private final SimuladorPartido simulador;
    private final GeneradorEnfrentamientos.Enfrentamiento enfrentamiento;
    private final boolean permiteEmpate;

    public SimuladorPartidoCallable(SimuladorPartido simulador, GeneradorEnfrentamientos.Enfrentamiento enfrentamiento, boolean permiteEmpate) {
        this.simulador = simulador;
        this.enfrentamiento = enfrentamiento;
        this.permiteEmpate = permiteEmpate;
    }

    /**
     * Este método se ejecuta EN OTRO HILO cuando el ExecutorService
     * decide ejecutar esta tarea. No lo llame directamente; déjelo al
     * ExecutorService.
     */
    @Override
    public ResultadoPartido call() throws Exception {
        return simulador.simular(enfrentamiento.local(), enfrentamiento.visitante(), permiteEmpate);
    }
}
