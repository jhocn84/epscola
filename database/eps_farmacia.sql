-- =====================================================================
-- Sistema de Gestión de Turnos para Entrega de Medicamentos en una EPS
-- Script de creación de la base de datos (MySQL 8 / MariaDB de XAMPP)
--
-- Ejecución:
--   a) phpMyAdmin: pestaña "Importar" y seleccionar este archivo.
--   b) Consola:    mysql -u root -e "source database/eps_farmacia.sql"
--
-- Se puede ejecutar varias veces: no borra datos ni falla si la base ya existe.
-- Por eso mismo, si la tabla se creó con una versión anterior de este script, no
-- la modifica; para estrenar las restricciones e índices hay que recrearla:
--   DROP TABLE eps_farmacia.turnos;     (borra los turnos guardados)
-- y volver a ejecutar el script. La aplicación funciona con ambas versiones.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS eps_farmacia
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE eps_farmacia;

CREATE TABLE IF NOT EXISTS turnos (
    id_turno        INT AUTO_INCREMENT PRIMARY KEY,
    codigo_turno    VARCHAR(10)  NOT NULL,                     -- letra del tipo + correlativo: G015
    nombre_usuario  VARCHAR(100) NOT NULL,
    documento       VARCHAR(20)  NOT NULL,
    tipo_turno      ENUM('GENERAL', 'PREFERENCIAL', 'RAPIDA') NOT NULL,
    prioridad       INT          NOT NULL,                     -- 1 = P, 2 = R, 3 = G
    id_ventanilla   INT          NOT NULL,                     -- 1 a 4
    hora_generacion DATETIME     NOT NULL,
    hora_atencion   DATETIME     NULL,                         -- NULL hasta ser ATENDIDO
    estado          ENUM('PENDIENTE', 'LLAMADO', 'ATENDIDO', 'CANCELADO')
                                 NOT NULL DEFAULT 'PENDIENTE',

    -- TurnoDAO.actualizarEstado() localiza el registro por su código,
    -- por lo que el código no se puede repetir.
    UNIQUE KEY uq_codigo_turno (codigo_turno),

    -- Al iniciar, la aplicación busca los turnos PENDIENTE y LLAMADO para reconstruir
    -- las colas. Son muy pocos frente a todo el historial: el índice evita leerlo entero.
    KEY idx_estado (estado),

    -- El historial y las estadísticas filtran por un rango de fechas y ordenan por hora.
    KEY idx_hora_generacion (hora_generacion),

    -- Las reglas del negocio también se validan en la base de datos, de modo que ni
    -- un cambio manual en phpMyAdmin pueda dejar un turno incoherente.

    -- La letra del código y la prioridad siempre corresponden al tipo de turno.
    CONSTRAINT chk_turno_tipo CHECK (
           (tipo_turno = 'PREFERENCIAL' AND prioridad = 1 AND codigo_turno LIKE 'P%')
        OR (tipo_turno = 'RAPIDA'       AND prioridad = 2 AND codigo_turno LIKE 'R%')
        OR (tipo_turno = 'GENERAL'      AND prioridad = 3 AND codigo_turno LIKE 'G%')
    ),

    -- La farmacia tiene exactamente 4 ventanillas.
    CONSTRAINT chk_turno_ventanilla CHECK (id_ventanilla BETWEEN 1 AND 4),

    -- Un turno tiene hora de atención si, y solo si, fue ATENDIDO.
    CONSTRAINT chk_turno_hora_atencion CHECK (
        (estado = 'ATENDIDO') = (hora_atencion IS NOT NULL)
    )
) ENGINE = InnoDB;

-- Para dejar la base limpia antes de una demostración:
--   TRUNCATE TABLE turnos;
