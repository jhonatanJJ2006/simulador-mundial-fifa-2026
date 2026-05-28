package ec.edu.utpl.computacion.proava.mundialsimulador.simulador;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Equipo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Grupo;
import java.util.ArrayList;
import java.util.List;

public final class GeneradorEnfrentamientos {

    private GeneradorEnfrentamientos() {}

    public record Enfrentamiento(Equipo local, Equipo visitante, Grupo grupo) {}

    public static List<Enfrentamiento> deUnGrupo(Grupo grupo) {
        List<Equipo> equipos = grupo.getEquipos();
        List<Enfrentamiento> resultado = new ArrayList<>(6);

        for (var i = 0; i < equipos.size(); i++) {
            for (var j = i + 1; j < equipos.size(); j++) {
                resultado.add(
                    new Enfrentamiento(equipos.get(i), equipos.get(j), grupo)
                );
            }
        }
        return resultado;
    }

    public static List<Enfrentamiento> deTodosLosGrupos(List<Grupo> grupos) {
        List<Enfrentamiento> todos = new ArrayList<>(72);
        for (var grupo : grupos) {
            todos.addAll(deUnGrupo(grupo));
        }
        return todos;
    }
}
