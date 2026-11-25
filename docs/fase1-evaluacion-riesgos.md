# Fase 1 – Evaluación de riesgos

## Contexto

El sistema es un originador de créditos conectado al motor antifraude, el buró y el core bancario. Debe soportar 1.500 solicitudes por segundo, cumplir un SLA de 99,9 % y guardar trazabilidad por 5 años.

Revisé el CSV de vulnerabilidades y el código del proyecto (`SecurityConfig.java`, `SecurityUtils.java` y `serenity.conf`).

## Metodología

Para cada vulnerabilidad evalué qué tan fácil es explotarla (probabilidad) y qué tan grave sería para el negocio (impacto).

|  | Baja | Media | Alta |
|---|---|---|---|
| **Probabilidad** | Requiere acceso interno y conocimiento | Requiere algo de esfuerzo | Cualquiera puede explotarla |
| **Impacto** | Molestia, sin datos expuestos | Afecta a algunos usuarios o datos | Dinero, datos de clientes o producción |

El riesgo sale de combinar ambas: Alta + Alta = Crítico, Alta + Media = Alto, y así sucesivamente. Cuando una vulnerabilidad toca dinero o datos de clientes, le di más peso al impacto.

---

## 1. Vulnerabilidades identificadas

Revisé las 15 vulnerabilidades del CSV. Tres figuran como resueltas, pero no lo están o no se puede confirmar:

- **VULN-004:** `serenity.conf` sigue teniendo contraseñas en texto plano de todos los ambientes, incluido producción, y la clave del JWT.
- **VULN-010:** los métodos de auditoría (`registrarEventoAutenticacion` y `registrarTrazabilidad`) no están implementados.
- **VULN-008:** en los archivos revisados no se puede confirmar que el debug esté apagado.

En el código también confirmé dos que siguen pendientes:

- **VULN-005:** CSRF está desactivado en `SecurityConfig.java` (`csrf.disable()`).
- **VULN-003:** el método `getMaxIntentosLogin` no está implementado, así que no hay bloqueo de intentos.

---

## 2. Evaluación de riesgos

| ID | Tipo | Probabilidad | Impacto | Riesgo | Por qué |
|---|---|---|---|---|---|
| VULN-004 | Credenciales en texto plano | Alta | Alto | Crítico | Con acceso al repo se puede entrar a producción. |
| VULN-001 | Inyección SQL | Alta | Alto | Crítico | Permite sacar datos de clientes desde el buscador. |
| VULN-003 | Sin bloqueo de intentos | Alta | Alto | Crítico | Se pueden probar contraseñas hasta entrar. |
| VULN-006 | Acceso a datos de otros | Alta | Alto | Crítico | Cambiando el ID en la URL se ven datos de otro cliente. |
| VULN-014 | Inyección de comandos | Alta | Alto | Crítico | Permite tomar el control del servidor. |
| VULN-005 | CSRF | Media | Alto | Alto | Permite hacer transferencias a nombre del cliente. |
| VULN-007 | Deserialización insegura | Media | Alto | Alto | Permite ejecutar código, pero requiere conocimiento técnico. |
| VULN-010 | Sin auditoría | Alta | Alto | Alto | No queda registro de ataques ni se cumplen los 5 años. |
| VULN-013 | SSRF | Media | Alto | Alto | Permite llegar a servicios internos como el core bancario. |
| VULN-012 | XXE | Media | Alto | Alto | Permite leer archivos o llenar la memoria del servidor. |
| VULN-009 | Librería vulnerable | Media | Alto | Alto | Permite ejecutar código; el CSV no dice cuál librería es. |
| VULN-011 | Inyección LDAP | Media | Alto | Alto | Permite ver o cambiar datos del directorio de usuarios. |
| VULN-002 | XSS reflejado | Media | Alto | Alto | Un enlace malicioso puede robar la sesión del cliente. |
| VULN-015 | Path Traversal | Media | Medio | Medio | Permite leer archivos fuera de la carpeta permitida. |
| VULN-008 | Debug en producción | Baja | Medio | Medio | Si sigue activo, expone información técnica. |

**Resumen:** 5 críticas, 8 altas y 2 medias.

Lo que más afecta el **SLA** es VULN-014 (puede apagar el servidor) y VULN-012 (puede llenar la memoria). La **trazabilidad de 5 años** hoy no se cumple por VULN-010.

---

## 3. Plan de mitigación

| ID | Medida | Prioridad | Responsable | Riesgo residual |
|---|---|---|---|---|
| VULN-004 | Mover secretos a un gestor y cambiar todas las claves | 1 | Equipo DevOps | Bajo: limpiar el historial de Git |
| VULN-014 | No ejecutar comandos con datos del usuario | 1 | Equipo de desarrollo | Bajo: revisar otros campos |
| VULN-001 | Usar `PreparedStatement` en todas las consultas | 2 | Equipo de desarrollo | Bajo: revisar consultas antiguas |
| VULN-005 | Activar CSRF en `SecurityConfig.java` | 2 | Equipo de desarrollo | Bajo: revisar todos los formularios |
| VULN-006 | Validar que cada usuario solo vea sus datos | 2 | Equipo de desarrollo | Bajo: revisar todos los servicios |
| VULN-007 | Recibir datos en JSON en lugar de objetos Java | 2 | Equipo de desarrollo | Bajo: revisar librerías |
| VULN-003 | Bloquear la cuenta tras 5 intentos fallidos | 3 | Equipo de desarrollo | Medio: bloqueos a propósito |
| VULN-010 | Implementar auditoría y guardarla 5 años | 3 | Equipo de desarrollo | Bajo: no guardar datos sensibles |
| VULN-013 | Permitir solo URLs autorizadas | 3 | Equipo de desarrollo | Bajo: mantener la lista |
| VULN-012 | Desactivar DTD en el lector de XML | 3 | Equipo de desarrollo | Bajo: revisar otros lectores XML |
| VULN-009 | Identificar y actualizar la librería | 3 | Equipo de desarrollo | Medio: no se sabe cuál es |
| VULN-011 | Escapar caracteres en búsquedas LDAP | 3 | Equipo de desarrollo | Bajo: revisar otras búsquedas |
| VULN-002 | Escapar lo que se muestra y proteger cookies | 3 | Equipo de desarrollo | Bajo: revisar otros formularios |
| VULN-015 | Validar la ruta de los archivos | 4 | Equipo de desarrollo | Bajo: revisar otras lecturas |
| VULN-008 | Confirmar que el debug está apagado | 4 | Equipo DevOps | Bajo: evitar que se reactive |

---

## 4. Observaciones

**Errores en el CSV:**

- VULN-008 tiene CVSS 6.5 pero dice "Alta"; según la escala de CVSS debería ser "Media".
- VULN-004 y VULN-010 figuran como resueltas, pero siguen abiertas.
- VULN-009 no indica la librería ni el CVE real.
- La mitigación de VULN-011 ("usar JNDI") está mal, porque JNDI es por donde entra el ataque.

**Diferencias con el CSV:**

- VULN-005 sube de Media a Alto porque permite hacer transferencias, es decir, toca dinero.
- VULN-006 sube a Crítico porque expone datos de clientes y es muy fácil de explotar.
- VULN-007, VULN-009 y VULN-012 bajan a Alto porque requieren conocimiento técnico.

**Discrepancia con el feature:**

El feature espera 1 crítica, 2 altas, 2 medias y 1 baja, pero esta evaluación da 5 críticas, 8 altas, 2 medias y ninguna baja. Los datos y la prueba no coinciden; lo dejo como hallazgo en lugar de ajustar los datos para que la prueba pase.
