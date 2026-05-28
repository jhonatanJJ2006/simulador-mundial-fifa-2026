package ec.edu.utpl.computacion.proava.mundialsimulador.simulador;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Grupo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.GeneradorEnfrentamientos.Enfrentamiento;
import java.util.ArrayList;
import java.util.List;

/**
 * Simula la fase de grupos del Mundial 2026 de forma SECUENCIAL.
 *
 * Esta clase es el "baseline" contra el que vamos a comparar la versión
 * paralela. Su único propósito es servir de referencia: tarda lo que tarda
 * sumar todos los partidos uno tras otro.
 *
 * A nivel de diseño: ¿por qué tenemos dos clases en vez de una con un
 * flag "modo paralelo"? Porque mezclar ambos modos en una sola clase la
 * vuelve más compleja y oculta la diferencia conceptual. Mantenerlas
 * separadas hace el contraste pedagógico muy claro: aquí no hay hilos,
 * en la otra sí.
 */
public class SimuladorFaseGrupos {

    private final SimuladorPartido simuladorPartido;

    public SimuladorFaseGrupos(SimuladorPartido simuladorPartido) {
        this.simuladorPartido = simuladorPartido;
    }

    /**
     * Simula los 72 partidos de la fase de grupos uno tras otro.
     * Retorna los resultados en el orden en que se simularon.
     */
    public List<ResultadoPartido> simular(List<Grupo> grupos) {
        List<Enfrentamiento> enfrentamientos =
            GeneradorEnfrentamientos.deTodosLosGrupos(grupos);

        List<ResultadoPartido> resultados = new ArrayList<>(
            enfrentamientos.size()
        );

        for (Enfrentamiento e : enfrentamientos) {
            // En fase de grupos: permiteEmpate = true
            ResultadoPartido r = simuladorPartido.simular(
                e.local(),
                e.visitante(),
                true
            );
            resultados.add(r);
        }

        return resultados;
    }
}
