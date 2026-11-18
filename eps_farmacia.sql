-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Servidor: 127.0.0.1
-- Tiempo de generación: 18-11-2026 a las 14:10:38
-- Versión del servidor: 10.4.32-MariaDB
-- Versión de PHP: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de datos: `eps_farmacia`
--

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `turnos`
--

CREATE TABLE `turnos` (
  `id_turno` int(11) NOT NULL,
  `codigo_turno` varchar(10) NOT NULL,
  `nombre_usuario` varchar(100) NOT NULL,
  `documento` varchar(20) NOT NULL,
  `tipo_turno` enum('GENERAL','PREFERENCIAL','RAPIDA') NOT NULL,
  `prioridad` int(11) NOT NULL,
  `id_ventanilla` int(11) NOT NULL,
  `hora_generacion` datetime NOT NULL,
  `hora_atencion` datetime DEFAULT NULL,
  `estado` enum('PENDIENTE','LLAMADO','ATENDIDO','CANCELADO') NOT NULL DEFAULT 'PENDIENTE'
) ;

--
-- Volcado de datos para la tabla `turnos`
--

INSERT INTO `turnos` (`id_turno`, `codigo_turno`, `nombre_usuario`, `documento`, `tipo_turno`, `prioridad`, `id_ventanilla`, `hora_generacion`, `hora_atencion`, `estado`) VALUES
(1, 'P001', 'Jhoan Sebastian Torres Garcia', '548744512', 'PREFERENCIAL', 1, 1, '2026-11-14 22:55:17', '2026-11-14 22:56:04', 'ATENDIDO'),
(2, 'G001', 'Juan', '878545451212', 'GENERAL', 3, 2, '2026-11-14 22:55:34', '2026-11-14 22:56:10', 'ATENDIDO'),
(3, 'G002', 'Andres', '5878423121', 'GENERAL', 3, 3, '2026-11-14 22:55:44', '2026-11-14 22:56:12', 'ATENDIDO'),
(4, 'G003', 'Felipe', '45454512', 'GENERAL', 3, 4, '2026-11-14 22:55:54', '2026-11-14 22:56:13', 'ATENDIDO'),
(5, 'G004', 'David', '23213434', 'GENERAL', 3, 1, '2026-11-14 22:56:02', NULL, 'CANCELADO');

--
-- Índices para tablas volcadas
--

--
-- Indices de la tabla `turnos`
--
ALTER TABLE `turnos`
  ADD PRIMARY KEY (`id_turno`),
  ADD UNIQUE KEY `uq_codigo_turno` (`codigo_turno`),
  ADD KEY `idx_estado` (`estado`),
  ADD KEY `idx_hora_generacion` (`hora_generacion`);

--
-- AUTO_INCREMENT de las tablas volcadas
--

--
-- AUTO_INCREMENT de la tabla `turnos`
--
ALTER TABLE `turnos`
  MODIFY `id_turno` int(11) NOT NULL AUTO_INCREMENT;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
