# Fase 4 – Integración y revisión de las pruebas de seguridad

## Contexto

Las Fases 1 a 3 revisaron la aplicación. Esta fase revisa las pruebas: ¿la suite del proyecto detectaría realmente los problemas que encontramos?

Para responder, ejecuté la suite con `mvn clean test` y revisé el runner (`RunCucumberTest.java`), los step definitions, las clases `VerificarVulnerabilidadSQL` y `ValidarVulnerabilidad`, y los tres features.

Al final hay un glosario con los términos técnicos explicados de forma sencilla.

---

## 1. Integración de las medidas

### Orden de implementación

| Paso | Qué se hace | Viene de | Por qué en este orden |
|---|---|---|---|
| 1 | Sacar contraseñas y claves del repositorio, guardarlas en un gestor de secretos y cambiarlas | Fase 1 (VULN-004) y Fase 3 | Es lo más urgente, y además el JWT y el cifrado necesitan ese gestor para guardar sus claves |
| 2 | Consultas parametrizadas, validación de formato y no ejecutar comandos con datos del usuario | Fase 1 (VULN-001 y 014) y Fase 2 | Cierra los ataques que permiten sacar datos o tomar el servidor |
| 3 | Login con JWT RS256, bloqueo de intentos, CSRF, roles y validación de que cada usuario vea solo sus datos | Fase 1 (VULN-003, 005 y 006) y Fase 3 | Depende del paso 1 para la clave del JWT |
| 4 | Cifrado AES-256-GCM de datos sensibles y bcrypt para contraseñas | Fase 3 | Depende del paso 1 para la clave de cifrado |
| 5 | Registros de auditoría protegidos con hash | Fase 1 (VULN-010), Fase 2 y Fase 3 | El bloqueo de IP y las alertas necesitan estos registros para funcionar |
| 6 | Capas extra: MFA, WAF y análisis estático en el pipeline | Fases 2 y 3 | Refuerzan lo anterior, pero no lo reemplazan |

### Capas del sistema

| Capa | Medidas |
|---|---|
| Comunicación | TLS 1.2 o superior y WAF |
| Aplicación | Consultas parametrizadas, validación de entradas, CSRF, JWT, roles, bloqueo de intentos y MFA |
| Datos | Cifrado AES-256-GCM, bcrypt y permisos mínimos en la base de datos |
| Monitoreo | Registros de auditoría, alertas y bloqueo de IP |
| Desarrollo | Análisis estático y la suite de pruebas corregida |

**Ninguna medida protege sola.** Si una capa falla, la siguiente sigue protegiendo. Por ejemplo, si un atacante pasa el WAF, las consultas parametrizadas impiden la inyección; y si alguien roba la base de datos, las contraseñas siguen protegidas con bcrypt.

---

## 2. Revisión de la suite de pruebas

Al ejecutar `mvn clean test`, el resultado fue:

```
Tests run: 35, Failures: 0, Errors: 0, Skipped: 35
BUILD SUCCESS
```

Ningún escenario falló, pero tampoco pasó ninguno: no se ejecutó nada. Revisando el proyecto encontré por qué, y otros problemas más:

| # | Hallazgo | Evidencia | Consecuencia |
|---|---|---|---|
| 1 | El runner filtra por un tag que no existe | `FILTER_TAGS = "@seguridad"`; ningún feature lo usa | 35 escenarios omitidos y BUILD SUCCESS |
| 2 | Los pasos de los features no están conectados al código | 0 de 213 pasos coinciden con los steps de Java | Aunque se corrija el tag, ningún escenario se ejecutaría |
| 3 | 28 métodos sin implementar | Lanzan `UnsupportedOperationException` | Si se conectaran, fallarían por "falta código", no por un problema de seguridad |
| 4 | La prueba de inyección SQL ignora ataques | `VerificarVulnerabilidadSQL` solo envía payloads de su lista o que coincidan con su patrón. El del propio feature (`1234567890' OR '1'='1`) no cumple ninguna de las dos y se marca "no vulnerable" sin enviarse | El ataque principal del feature nunca se prueba |
| 5 | La prueba de SQL solo detecta errores visibles | Una respuesta 200 con datos de más se toma como "sin problemas" | Una inyección exitosa pasaría como segura |
| 6 | La prueba de SQL siempre usa el mismo endpoint | Envía todo a `/api/v1/consultas/buscar`, sin importar el endpoint indicado | Login, saldo y buró nunca se prueban |
| 7 | Una validación funciona al revés | `ValidarVulnerabilidad` marca un 403 (bloqueo correcto) como Path Traversal | Una protección que funciona se reporta como falla |
| 8 | No hay pipeline | No existe configuración de CI en el repo | No se sabe cuándo se ejecutan las pruebas |

**Una suite en verde no significa que la aplicación sea segura.** Hoy la suite da una falsa sensación de seguridad: dice BUILD SUCCESS sin haber probado nada, y si se conectara, varias de sus validaciones no detectarían los ataques o los reportarían al revés.

No modifiqué el runner: el hallazgo es cómo funciona hoy. La corrección va en la sección 3.

---

## 3. Propuestas de mejora

### Corregir la suite actual

| Mejora | Resuelve |
|---|---|
| Cambiar el filtro del runner por tags que sí existen (`@smoke`, `@regression`) o agregar `@seguridad` a los features | Hallazgo 1 |
| Escribir los step definitions con las mismas frases de los features | Hallazgo 2 |
| Implementar los 28 métodos pendientes | Hallazgo 3 |
| Hacer que la prueba de SQL envíe todos los payloads, use el endpoint indicado y compare respuestas (por ejemplo, `AND 1=1` contra `AND 1=2`, o el tiempo de respuesta) en lugar de buscar solo mensajes de error | Hallazgos 4, 5 y 6 |
| Corregir la validación que toma un 403 como vulnerabilidad | Hallazgo 7 |
| Configurar la suite para que falle si no se ejecuta ningún escenario o si hay pasos sin definir | Evita que vuelva a dar un falso BUILD SUCCESS |

### Agregar escenarios que faltan

Varias vulnerabilidades críticas o altas del CSV no tienen ningún escenario en los features: **CSRF** (VULN-005), **inyección de comandos** (VULN-014), **deserialización** (VULN-007) y **SSRF** (VULN-013). También agregaría uno que revise que **no haya contraseñas ni claves en el repositorio**, porque fue el problema más grave que encontramos y ninguna prueba lo detectaría.

### Pipeline y otros tipos de prueba

Cucumber prueba lo que uno le pide, pero no encuentra problemas nuevos por sí solo. Por eso propongo un pipeline que corra en cada cambio de código con:

| Tipo de prueba | Qué aporta |
|---|---|
| La suite de Cucumber corregida | Comprueba que las protecciones funcionan |
| Análisis estático (SAST) | Encuentra consultas mal armadas y otros errores en el código |
| Análisis de dependencias (SCA) | Detecta librerías con vulnerabilidades conocidas (VULN-009) |
| Detección de secretos | Bloquea el cambio si alguien sube una contraseña o clave |
| Pruebas dinámicas (DAST) en staging | Ataca la aplicación funcionando, como lo haría un atacante |

Si no hay infraestructura para un pipeline, lo mismo se puede hacer localmente con plugins de Maven (SpotBugs con FindSecBugs y OWASP Dependency-Check), un pre-commit hook (Gitleaks) y OWASP ZAP en Docker. La desventaja es que depende de que cada persona lo ejecute.

Además, propongo una **prueba de penetración manual** al menos una vez al año y antes de cambios grandes, porque una persona encuentra fallas de lógica que las herramientas no ven.

### Mejora continua

- Repetir la evaluación de riesgos **cada mes**, como sugiere el feature (`proxima_evaluacion` es un mes después de `fecha_evaluacion`), y también después de cada cambio importante.
- No marcar una vulnerabilidad como "Resuelta" sin una prueba que lo demuestre.

---

## 4. Conclusión

**Fase 1** mostró que el CSV no era confiable: dos vulnerabilidades marcadas como resueltas seguían abiertas, y la más grave fue tener contraseñas de producción y la clave del JWT en el repositorio. También mostró que el CVSS no basta y hay que pensar en el impacto para el negocio.

**Fase 2** identificó siete puntos de entrada a proteger contra inyección SQL y concluyó que las consultas parametrizadas son la única defensa que elimina el problema; las demás medidas son capas extra.

**Fase 3** propuso un esquema con JWT RS256, bloqueo de intentos, MFA, roles con funciones separadas y cifrado AES-256-GCM, y dejó registradas las contradicciones entre el feature y la configuración.

**Fase 4** reveló que la suite de pruebas da un BUILD SUCCESS sin probar nada: el filtro del runner no coincide con ningún feature y ningún paso está conectado al código.

**La lección principal:** una suite en verde no significa que la aplicación sea segura. La seguridad se demuestra con pruebas que de verdad se ejecutan, con varias capas de protección y con revisiones periódicas.

---

## Glosario

| Término | Qué significa |
|---|---|
| Suite de pruebas | El conjunto de todas las pruebas automáticas de un proyecto. |
| Cucumber | Una herramienta que ejecuta pruebas escritas en lenguaje casi natural ("Dado... Cuando... Entonces..."). |
| Feature | Un archivo que describe con frases sencillas qué debe hacer el sistema y cómo se prueba. |
| Escenario | Un caso de prueba dentro de un feature. |
| Step definition | El código que le dice a Cucumber qué hacer con cada frase de un escenario. Si la frase no coincide exactamente, el paso queda sin definir. |
| Runner | La clase que indica qué features se ejecutan y con qué configuración. |
| Tag | Una etiqueta (como `@smoke`) que se pone a un escenario para poder filtrarlo. |
| BUILD SUCCESS | El mensaje de Maven cuando la ejecución termina sin fallos. No significa que se haya probado algo. |
| Skipped | Una prueba que se omitió: no pasó ni falló, simplemente no se ejecutó. |
| Payload | El texto malicioso que se envía para intentar un ataque. |
| Endpoint | Una dirección específica de la API, como `/api/v1/cuenta/saldo`. |
| Pipeline (CI) | Un proceso automático que ejecuta las pruebas cada vez que alguien sube cambios al código. |
| SAST | Análisis estático: revisar el código con una herramienta, sin ejecutarlo, para encontrar errores de seguridad. |
| SCA | Análisis de dependencias: revisar las librerías que usa el proyecto para ver si tienen fallas conocidas. |
| DAST | Pruebas dinámicas: atacar la aplicación mientras funciona, como lo haría un atacante. |
| Prueba de penetración | Un ataque controlado hecho por una persona experta para encontrar fallas que las herramientas no ven. |
| Pre-commit hook | Una revisión automática que se ejecuta cada vez que alguien hace commit y puede bloquearlo si encuentra un problema. |
| Gestor de secretos | Un sistema especial para guardar contraseñas y claves de forma segura, fuera del código. |
| Capas de protección | Usar varias medidas de seguridad a la vez, para que si una falla, otra siga protegiendo. |
