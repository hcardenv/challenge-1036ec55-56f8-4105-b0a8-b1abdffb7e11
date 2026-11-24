package com.banca.seguridad.questions;

import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Question;
import net.serenitybdd.screenplay.questions.Text;
import net.serenitybdd.screenplay.waits.WaitUntil;
import net.serenitybdd.screenplay.matchers.WebElementStateMatchers;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import com.banca.seguridad.exceptions.SecurityException;
import io.restassured.response.Response;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static net.serenitybdd.screenplay.GivenWhenThen.seeThat;
import static org.hamcrest.Matchers.*;

public class ValidarAcceso implements Question<Boolean> {

    private final String recurso;
    private final String tipoAcceso;
    private final By locator;

    private ValidarAcceso(String recurso, String tipoAcceso, By locator) {
        this.recurso = recurso;
        this.tipoAcceso = tipoAcceso;
        this.locator = locator;
    }

    public static ValidarAcceso alRecurso(String recurso) {
        return new ValidarAcceso(recurso, "lectura", null);
    }

    public static ValidarAcceso alRecurso(String recurso, String tipoAcceso) {
        return new ValidarAcceso(recurso, tipoAcceso, null);
    }

    public ValidarAcceso enLaPagina(By locator) {
        return new ValidarAcceso(this.recurso, this.tipoAcceso, locator);
    }

    public static ValidarAcceso conPermisos(String recurso, String tipoPermiso) {
        return new ValidarAcceso(recurso, tipoPermiso, null);
    }

    public static Question<Boolean> aMenuPrincipal() {
        return Question.about("acceso al menu principal")
            .answeredBy(actor -> codigoDeEstado(actor) == 200 && tokenRecordado(actor) != null);
    }

    public static Question<Boolean> conTokenValido() {
        return Question.about("token JWT valido")
            .answeredBy(actor -> esJwtBienFormado(tokenRecordado(actor)));
    }

    public static Question<Boolean> conErrorDeAutenticacion() {
        return Question.about("error de autenticacion")
            .answeredBy(actor -> codigoDeEstado(actor) == 401);
    }

    public static Question<Boolean> conCodigoDeEstado(int codigoEsperado) {
        return Question.about("codigo de estado " + codigoEsperado)
            .answeredBy(actor -> codigoDeEstado(actor) == codigoEsperado);
    }

    public static Question<Boolean> conIntentoDeInyeccionBloqueado() {
        return Question.about("intento de inyeccion bloqueado")
            .answeredBy(actor -> {
                Map<String, Object> ultimoTest = actor.recall("ultimo_test_sql");
                return ultimoTest != null && Boolean.FALSE.equals(ultimoTest.get("vulnerable"));
            });
    }

    public static Question<Boolean> sinEjecucionDeInyeccion() {
        return Question.about("consulta maliciosa no ejecutada")
            .answeredBy(actor -> {
                int codigo = codigoDeEstado(actor);
                return codigo == 400 || codigo == 403;
            });
    }

    public static Question<Boolean> conPermiso(String permiso) {
        return Question.about("token con permiso " + permiso)
            .answeredBy(actor -> {
                String token = tokenRecordado(actor);
                return esJwtBienFormado(token) && payloadDelToken(token).contains("\"" + permiso + "\"");
            });
    }

    public static Question<Boolean> conTokenInvalido() {
        return Question.about("token rechazado")
            .answeredBy(actor -> codigoDeEstado(actor) == 401);
    }

    public static Question<Boolean> registradoEnLog() {
        return Question.about("intento registrado en el log de seguridad")
            .answeredBy(actor -> ultimaRespuesta(actor).getHeader("X-Audit-Id") != null);
    }

    public static Question<Boolean> conCuentaBloqueada() {
        return Question.about("cuenta bloqueada")
            .answeredBy(actor -> codigoDeEstado(actor) == 423);
    }

    public static Question<Boolean> conMensajeDeCuentaBloqueada() {
        return Question.about("mensaje de cuenta bloqueada")
            .answeredBy(actor -> ultimaRespuesta(actor).getBody().asString().toLowerCase().contains("bloquead"));
    }

    public static Question<Boolean> con2FAValido() {
        return Question.about("acceso concedido con 2FA")
            .answeredBy(actor -> codigoDeEstado(actor) == 200 && esJwtBienFormado(tokenRecordado(actor)));
    }

    private static Response ultimaRespuesta(Actor actor) {
        return LastResponse.received().answeredBy(actor);
    }

    private static int codigoDeEstado(Actor actor) {
        return ultimaRespuesta(actor).getStatusCode();
    }

    private static String tokenRecordado(Actor actor) {
        return actor.recall("token_jwt");
    }

    private static boolean esJwtBienFormado(String token) {
        return token != null && token.split("\\.").length == 3;
    }

    private static String payloadDelToken(String token) {
        byte[] payload = Base64.getUrlDecoder().decode(token.split("\\.")[1]);
        return new String(payload, StandardCharsets.UTF_8);
    }

    @Override
    public Boolean answeredBy(Actor actor) {
        try {
            if (locator != null) {
                actor.attemptsTo(
                    WaitUntil.the(locator, WebElementStateMatchers.isVisible())
                        .forNoMoreThan(10).seconds()
                );
                
                WebElement elemento = locator.findElement(null);
                String contenido = elemento.getText();
                
                actor.should(
                    seeThat("El recurso debe ser accesible",
                        actor1 -> contenido,
                        not(isEmptyString())
                    )
                );
                
                return validarPermisosAcceso(contenido);
            }
            
            return validarPermisosAcceso(actor.recall("ultimoAcceso"));
            
        } catch (Exception e) {
            throw new SecurityException(
                "Error al validar acceso al recurso: " + recurso + ", tipo: " + tipoAcceso, e
            );
        }
    }

    private boolean validarPermisosAcceso(String contenido) {
        if (contenido == null || contenido.isEmpty()) {
            return false;
        }
        
        if ("administrador".equals(tipoAcceso)) {
            return contenido.contains("ACCESS_GRANTED") || 
                   contenido.contains("ROLE_ADMIN") ||
                   contenido.contains("全権");
        }
        
        if ("lectura".equals(tipoAcceso)) {
            return contenido.contains("READ") || 
                   contenido.contains("LECTURA") ||
                   contenido.contains("VIEW");
        }
        
        if ("escritura".equals(tipoAcceso) || "modificacion".equals(tipoAcceso)) {
            return contenido.contains("WRITE") || 
                   contenido.contains("ESCRITURA") ||
                   contenido.contains("MODIFY");
        }
        
        return contenido.contains("ACCESS_GRANTED");
    }

    public String getRecurso() {
        return recurso;
    }

    public String getTipoAcceso() {
        return tipoAcceso;
    }

    public By getLocator() {
        return locator;
    }
}