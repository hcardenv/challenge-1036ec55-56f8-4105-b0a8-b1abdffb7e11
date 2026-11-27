# Fase 3 – Autenticación, autorización y criptografía

## Contexto

La plataforma maneja información financiera sensible y debe cumplir con PCI-DSS. Los datos tienen que estar cifrados tanto cuando viajan como cuando se guardan, y solo los usuarios autorizados deben poder usar las funciones sensibles.

Para esta fase revisé el feature de autenticación, autorización y criptografía, y el código del proyecto (`SecurityConfig.java`, `SecurityUtils.java` y `serenity.conf`). En cada propuesta muestro cómo está hoy el código, para que se vea qué cambia.

Al final hay un glosario con los términos técnicos explicados de forma sencilla.

---

## 1. Esquema de autenticación y autorización

### Autenticación (quién eres)

| Componente | Propuesta | Cómo está hoy |
|---|---|---|
| Inicio de sesión | Documento + contraseña, con el mensaje genérico "Credenciales inválidas", que no revela si el usuario existe | HTTP Basic: usuario y contraseña viajan en cada petición |
| Bloqueo de intentos | 5 intentos fallidos en 15 minutos bloquean la cuenta 30 minutos, con aviso al correo del cliente | No existe (`getMaxIntentosLogin` sin implementar) |
| Token de sesión | JWT firmado con RS256 (clave privada RSA de 2048 bits en un gestor de secretos), con `sub`, `rol`, `permisos` y `exp`, enviado en la cabecera `Authorization: Bearer` | JWT con HS256, clave escrita en `serenity.conf` y firma que no coincide |
| Duración de la sesión | Cierre a los 30 minutos sin actividad, máximo 8 horas, y se invalida al cerrar sesión | Los tokens se guardan en un `HashMap` que nunca se limpia |
| Segundo factor (MFA) | Código OTP al celular para transferencias mayores a 10.000, o cuando el total del día supera ese monto | No existe |

### Autorización (qué puedes hacer)

| Componente | Propuesta |
|---|---|
| Roles | Cada usuario tiene un rol con permisos definidos: `consulta` (solo ver), `originador_creditos` (crear solicitudes) y `aprobador_creditos` (aprobarlas) |
| Propiedad de los datos | En cada petición se verifica que la cuenta pedida pertenezca al usuario; si no, responde 403. Esto corrige VULN-006 |
| Accesos negados | Todo intento sin permiso responde 403 y queda registrado |

**Separación de funciones:** el feature permite que el rol `originador_creditos` también apruebe créditos. Eso significa que la misma persona podría crear una solicitud y aprobarla, lo que facilita el fraude. Por eso propongo un rol aparte, `aprobador_creditos`: uno crea y otro aprueba.

---

## 2. Evaluación de la seguridad

| Componente | Qué protege | Debilidad | Cómo se cubre |
|---|---|---|---|
| Mensaje genérico en el login | Que un atacante sepa qué usuarios existen | Si el sistema tarda distinto cuando el usuario existe, igual se puede adivinar | Responder siempre en un tiempo parecido |
| Bloqueo tras 5 intentos | Fuerza bruta contra una cuenta | Alguien puede bloquear cuentas a propósito, o probar una sola contraseña en miles de cuentas sin llegar al límite | Aviso al correo del cliente y límite de intentos por IP |
| JWT con RS256 | Que alguien fabrique o modifique tokens | Un token robado sirve hasta que expira, aunque el cliente cierre sesión | Lista de tokens revocados con expiración automática (reemplaza el `HashMap`) |
| MFA por SMS | Transferencias grandes con una cuenta robada | El SMS se puede interceptar, y alguien podría hacer varias transferencias de 9.999 para no pedir el código | Pedir MFA también por monto acumulado en el día |
| Roles | Que un usuario use funciones que no le tocan | Un rol con demasiados permisos | Separar funciones (crear y aprobar créditos) |
| Propiedad de los datos | Que un cliente vea cuentas de otro | Basta con que un servicio no haga la validación | Validarlo en un solo lugar común para todos los servicios |

Cada componente se puede comprobar con los escenarios del feature.

---

## 3. Técnicas de criptografía

El objetivo es proteger la **confidencialidad** (que nadie lea los datos) y la **integridad** (que nadie los modifique sin que se note).

| Qué se protege | Técnica propuesta | Protege | Cómo está hoy |
|---|---|---|---|
| Datos en tránsito | TLS 1.2 o superior, con AES-256-GCM y certificados válidos. Se desactivan SSLv3, TLS 1.0 y 1.1 | Confidencialidad e integridad | Las pruebas ignoran errores de certificado (`--ignore-certificate-errors`) |
| Datos sensibles guardados (transacciones, tarjetas) | AES-256 en modo GCM, con un IV distinto en cada cifrado que se guarda junto al dato | Confidencialidad e integridad | Todavía no hay código que cifre. "AES" solo se usa para generar la clave, sin definir el modo, así que existe el riesgo de que se implemente con el modo por defecto (ECB) aunque `serenity.conf` pide GCM |
| Claves de cifrado | Guardadas en un gestor de claves y rotadas periódicamente | Confidencialidad | La clave se crea en memoria al arrancar, se pierde al reiniciar y cualquier parte del código la puede leer con `getClaveSecreta()` |
| Contraseñas | bcrypt con costo 12, que ya incluye un salt único por usuario | Confidencialidad | Se planea usar SHA-256, que es demasiado rápido |
| Tokens de sesión | Firma RS256: se firman con la clave privada y se verifican con la pública. Así, los servicios que verifican (antifraude, buró, core) no pueden fabricar tokens aunque uno de ellos sea atacado | Integridad | Firma HS256 con claves que no coinciden |
| Registros de auditoría | Cada registro lleva un hash del anterior, así que si alguien borra o cambia uno, se nota | Integridad | No hay registros de auditoría |

**Por qué importa el modo de AES:** el modo GCM no solo cifra, también detecta si alguien modificó el dato. El modo ECB, que Java usa por defecto si no se indica otro, no protege bien ninguna de las dos cosas. Por eso no basta con decir "usamos AES-256": hay que decir en qué modo.

**Números de tarjeta:** se cifran con AES-256-GCM, como pide el feature. Como mejora, PCI-DSS recomienda guardarlos lo menos posible: mostrar solo los últimos 4 dígitos y reemplazar el número por un código que no sirve fuera del banco (tokenización).

---

## Observaciones

- **Algoritmo del JWT:** el feature pide RS256, pero `serenity.conf` y el token de ejemplo del propio feature usan HS256. Propongo RS256, como pide el feature.
- **Separación de funciones:** el feature deja que un mismo rol cree y apruebe créditos. Propongo separarlos.
- **Rendimiento:** el feature pide 500 inicios de sesión por minuto con respuesta promedio menor a 200 ms, y también bcrypt con costo 12. Solo calcular ese hash puede tardar unos 250 ms en un servidor típico, y eso no se arregla sumando servidores: más servidores atienden más inicios de sesión a la vez, pero cada uno sigue tardando lo mismo. Propongo medirlo en el hardware de producción y, si no alcanza, ajustar el requisito de tiempo solo para el login (por ejemplo, a 300 ms) en lugar de bajar el costo de bcrypt. Iniciar sesión es algo poco frecuente, y unos milisegundos más ahí no afectan al cliente.

---

## Glosario

| Término | Qué significa |
|---|---|
| Autenticación | Comprobar quién eres, por ejemplo con tu documento y tu contraseña. |
| Autorización | Decidir qué puedes hacer una vez que el sistema sabe quién eres. |
| Rol | Un grupo de permisos. Por ejemplo, un usuario con rol "consulta" solo puede ver información. |
| JWT | Un "pase" digital que el sistema entrega al iniciar sesión. Dice quién eres y qué puedes hacer, y va firmado para que nadie lo altere. |
| HS256 y RS256 | Dos formas de firmar un JWT. HS256 usa la misma clave para firmar y verificar, así que todo el que verifica también podría firmar. RS256 usa una clave privada para firmar y una pública para verificar, así que los demás servicios pueden comprobar el token sin poder fabricarlo. |
| Clave pública y privada | Un par de claves que funcionan juntas. La privada se guarda en secreto y sirve para firmar; la pública se puede compartir y sirve para comprobar la firma. |
| MFA | Pedir una segunda prueba de identidad además de la contraseña, como un código enviado al celular. |
| OTP | Un código que sirve una sola vez y por poco tiempo, como el que llega por SMS. |
| Cifrar | Convertir un dato en algo ilegible que solo se puede volver a leer con la clave correcta. |
| AES-256-GCM | Un método para cifrar datos. "256" es el tamaño de la clave y "GCM" es el modo que además avisa si alguien modificó el dato. |
| IV | Un valor aleatorio que se usa en cada cifrado para que el mismo dato no se vea igual dos veces. |
| Hash | Un código que se calcula a partir de un dato y que no se puede revertir. Sirve para comprobar que algo no cambió. |
| bcrypt | Una forma de guardar contraseñas como un hash. Es lenta a propósito, para que adivinarlas tome mucho tiempo. |
| Salt | Un valor aleatorio que se agrega a cada contraseña antes de calcular el hash, para que dos contraseñas iguales no den el mismo resultado. |
| TLS | La protección que cifra la comunicación entre el navegador y el banco. Es el candado que se ve en la barra del navegador. |
| Gestor de claves | Un sistema especial para guardar claves y contraseñas de forma segura, fuera del código. |
| Tokenización | Reemplazar un dato sensible, como un número de tarjeta, por un código que no sirve fuera del banco. |
| PCI-DSS | Las normas de seguridad que deben cumplir las empresas que manejan tarjetas de pago. |
| Código 401, 403 y 423 | Respuestas del sistema: 401 = no te identificaste bien, 403 = no tienes permiso, 423 = la cuenta está bloqueada. |
