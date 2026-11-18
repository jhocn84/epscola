package com.mycompany.proyectoestructurasdedatos.persistencia;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Gestiona la conexión JDBC con MySQL aplicando el patrón Singleton: existe una
 * única instancia de esta clase y, por tanto, una única conexión activa durante
 * toda la ejecución. Abrir una conexión es costoso, y una por consulta agotaría
 * las conexiones del servidor.
 *
 * Elementos del patrón: constructor privado (nadie más puede hacer new), atributo
 * estático con la única instancia y método estático getInstancia() para obtenerla.
 *
 * Los datos de acceso son los de una instalación por defecto de XAMPP. Si su MySQL
 * tiene otra contraseña, cámbiela en CLAVE o ejecute con -Deps.db.clave=suClave.
 */
public class ConexionDB {

    private static final String URL = System.getProperty("eps.db.url",
            "jdbc:mysql://localhost:3306/eps_farmacia"
            + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000");
    private static final String USUARIO = System.getProperty("eps.db.usuario", "root");
    private static final String CLAVE = System.getProperty("eps.db.clave", "");

    private static ConexionDB instancia;

    private Connection conexion;

    private ConexionDB() {
    }

    /**
     * Devuelve la única instancia, creándola la primera vez que se pide.
     * Es synchronized para que dos hilos no puedan crear dos instancias a la vez.
     */
    public static synchronized ConexionDB getInstancia() {
        if (instancia == null) {
            instancia = new ConexionDB();
        }
        return instancia;
    }

    /**
     * Devuelve la conexión activa. Si no existe o ya no sirve (MySQL se reinició o
     * cerró la conexión por inactividad), abre una nueva, de modo que la aplicación
     * se recupera sola cuando el servidor vuelve.
     */
    public synchronized Connection getConexion() throws SQLException {
        if (conexion == null || !conexion.isValid(2)) {
            conexion = DriverManager.getConnection(URL, USUARIO, CLAVE);
        }
        return conexion;
    }

    /** Cierra la conexión al salir de la aplicación. */
    public synchronized void cerrarConexion() {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (SQLException e) {
                System.err.println("No se pudo cerrar la conexión: " + e.getMessage());
            }
            conexion = null;
        }
    }
}
