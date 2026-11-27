# Fase 2 – Inyección SQL y protección

## Contexto

La aplicación usa una base de datos relacional y los usuarios interactúan con ella a través de formularios y APIs. Como el sistema maneja cuentas, transacciones y consultas al buró, una inyección SQL puede exponer o borrar información de los clientes.

Para esta fase revisé el feature de inyección SQL, el CSV de vulnerabilidades (VULN-001) y el código del proyecto. Los métodos de consulta todavía no están implementados, así que no se puede confirmar en el código que cada punto sea vulnerable. Por eso los trato como **puntos de entrada a proteger**.

---

## 1. Puntos de entrada vulnerables

| # | Punto de entrada | Campo | Ejemplo de ataque | Qué podría pasar | Riesgo |
|---|---|---|---|---|---|
| 1 | Login | `usuario` | `admin'--` | Entrar como administrador sin contraseña | Crítico |
| 2 | Búsqueda de transacciones (VULN-001) | `criterio_busqueda` | `' OR '1'='1` | Ver las transacciones de todos los clientes | Crítico |
| 3 | API de saldo (`/api/v1/cuenta/saldo`) | `numero_cuenta` | `; DROP TABLE transacciones;--` | Borrar tablas completas | Crítico |
| 4 | Consulta al buró | `documento` | `UNION SELECT * FROM usuarios` | Sacar la tabla de usuarios | Crítico |
| 5 | Listado de transacciones | `fecha_inicio`, `fecha_fin` | `2024-01-01' AND '1'='1` | Ver datos de otras cuentas | Alto |
| 6 | Nueva transferencia | `cuenta_destino`, `monto`, `concepto` | Comandos SQL en el concepto | Alterar o borrar transacciones | Alto |
| 7 | Beneficiarios guardados (segunda orden) | `nombre_beneficiario` | `Roberto'); DROP TABLE...` | El ataque se guarda y se ejecuta después, en otra consulta | Alto |

El login y la API de saldo son los más graves: uno permite saltarse la autenticación y el otro borrar información. El de segunda orden es el más fácil de pasar por alto, porque el dato parece inofensivo cuando se guarda.

---

## 2. Medidas para prevenir y detectar

### Prevención

| Medida | Por qué |
|---|---|
| Usar consultas parametrizadas (`PreparedStatement`) en todas las consultas | Es la defensa principal: el dato del usuario nunca se mezcla con la orden SQL. |
| Validar el formato y la longitud de cada campo (fechas, cuentas, documento, monto) | Si un campo solo acepta números, no se pueden meter comandos. |
| Usar consultas parametrizadas también con datos ya guardados | Evita la inyección de segunda orden. |
| Dar permisos mínimos al usuario de la base de datos y mostrar errores genéricos | Si algo falla, el daño queda limitado y el atacante no recibe pistas. |

### Detección

| Medida | Por qué |
|---|---|
| Registrar cada intento con tipo de ataque, payload, IP y fecha | Deja evidencia y suma a la trazabilidad de 5 años. |
| Bloquear la IP por un tiempo después de 5 intentos sospechosos | Frena la inyección a ciegas, que necesita muchas pruebas. |
| Usar un WAF y la lista de patrones de `serenity.conf` | Detiene ataques conocidos antes de llegar a la aplicación. |
| Revisar el código con análisis estático en el pipeline | Encuentra consultas mal armadas antes de producción. |

### Decisión sobre la comilla simple

El feature pide rechazar la comilla simple (`'`) en todos los campos, incluido el concepto de la transferencia. Eso puede bloquear datos legítimos, como un beneficiario llamado "O'Brien".

Por eso decidí rechazar la comilla solo en los campos con formato fijo (cuenta, documento, fechas y monto). En los campos de texto libre, la protección la dan las consultas parametrizadas.

---

## 3. Evaluación de la efectividad

| Medida | Efectividad | Límite |
|---|---|---|
| Consultas parametrizadas | Alta | Solo protege las consultas donde se usa. |
| Validar formato | Alta en campos con formato fijo | No sirve en campos de texto libre. |
| Parametrizar datos guardados | Alta | Hay que aplicarlo también en procesos internos y reportes. |
| Permisos mínimos y errores genéricos | Media | No evitan el ataque, solo reducen el daño. |
| Registrar intentos | Media | Solo avisa; no bloquea nada. |
| Bloquear IP | Media | El atacante puede cambiar de IP, y se podría bloquear a clientes que comparten la misma IP. |
| WAF y patrones | Media | Se pueden evadir con variaciones del ataque. |
| Análisis estático | Alta | Puede dar falsos positivos. |

Cada medida se puede comprobar con los escenarios del feature.

**Conclusión:** las consultas parametrizadas son la única medida que por sí sola elimina el problema; las demás son capas extra. Si hubiera que elegir solo una, sería esa, y no el WAF o los patrones, que suelen ponerse primero porque son más rápidos de implementar.

---

## Observaciones

- **Error común:** bloquear caracteres en lugar de usar consultas parametrizadas. Además de ser fácil de evadir, rechaza datos válidos de los clientes.
- **Prueba de rendimiento:** el escenario de estrés usa 100 ataques a 10 por segundo, muy poco comparado con las 1.500 solicitudes por segundo del sistema. La prueba debería hacerse con una carga parecida a la real para saber si el WAF y los registros afectan el SLA.

---

## Glosario

| Término | Qué significa |
|---|---|
| Inyección SQL | Escribir comandos de base de datos en un campo del formulario para sacar o borrar información. |
| Consulta parametrizada (`PreparedStatement`) | Una forma de hacer consultas que separa el dato del usuario de la orden, para que no se pueda inyectar nada. |
| Punto de entrada | Cualquier lugar por donde el usuario envía datos al sistema: un formulario, un campo o un parámetro de una API. |
| Payload | El texto malicioso que el atacante escribe para intentar el ataque. |
| API | La puerta por donde otras aplicaciones se comunican con el sistema. |
| Inyección a ciegas | Un ataque en el que el atacante no ve los datos, pero los deduce haciendo muchas preguntas de sí o no y mirando cómo responde el sistema. |
| Inyección de segunda orden | El ataque se guarda primero como un dato normal y se ejecuta después, cuando ese dato se usa en otra consulta. |
| Sanitizar | Limpiar o rechazar los caracteres peligrosos de lo que escribe el usuario. |
| WAF | Un filtro que se pone delante de la aplicación y bloquea peticiones con ataques conocidos. |
| Análisis estático | Revisar el código con una herramienta automática, sin ejecutarlo, para encontrar errores de seguridad. |
| Falso positivo | Cuando una herramienta marca como problema algo que en realidad está bien. |
| Código 403 | La respuesta del sistema cuando bloquea una petición porque no está permitida. |
| SLA | El compromiso de disponibilidad del servicio. 99,9 % significa que puede estar caído menos de 9 horas al año. |
