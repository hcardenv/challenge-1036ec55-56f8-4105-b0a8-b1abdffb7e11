package com.banca.seguridad.interactions;

import io.restassured.response.Response;
import net.serenitybdd.screenplay.Actor;
import net.serenitybdd.screenplay.Interaction;
import net.serenitybdd.screenplay.rest.interactions.Get;
import net.serenitybdd.screenplay.rest.questions.LastResponse;

/**
 * Interaccion de bajo nivel: hace un GET autenticado a un recurso del API usando el token
 * JWT que el actor recuerda, y deja la respuesta en memoria del actor para que las
 * questions la validen ("ultimaRespuesta" y "ultimoAcceso").
 */
public class ConsultarRecurso implements Interaction {

    public static final String ENDPOINT_TRANSACCIONES = "/api/v1/transacciones";
    public static final String ENDPOINT_VALIDAR_TOKEN = "/api/v1/autenticacion/validar";

    private static final String TOKEN_SIN_FIRMA = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiJhdGFjYW50ZSJ9.";
    private static final String FIRMA_ALTERADA = "firmaAlterada";

    private final String recurso;
    private final boolean manipularToken;

    private ConsultarRecurso(String recurso, boolean manipularToken) {
        this.recurso = recurso;
        this.manipularToken = manipularToken;
    }

    public static ConsultarRecurso en(String recurso) {
        return new ConsultarRecurso(recurso, false);
    }

    public static ConsultarRecurso transacciones() {
        return new ConsultarRecurso(ENDPOINT_TRANSACCIONES, false);
    }

    public static ConsultarRecurso conTokenManipulado() {
        return new ConsultarRecurso(ENDPOINT_VALIDAR_TOKEN, true);
    }

    @Override
    public <T extends Actor> void performAs(T actor) {
        String token = tokenAEnviar(actor.recall("token_jwt"));

        actor.attemptsTo(
            Get.resource(recurso)
                .with(request -> token == null
                    ? request
                    : request.header("Authorization", "Bearer " + token))
        );

        Response respuesta = LastResponse.received().answeredBy(actor);
        actor.remember("ultimaRespuesta", respuesta);
        actor.remember("ultimoAcceso", respuesta.getBody().asString());
    }

    private String tokenAEnviar(String tokenOriginal) {
        if (!manipularToken) {
            return tokenOriginal;
        }
        if (tokenOriginal == null || tokenOriginal.lastIndexOf('.') < 0) {
            return TOKEN_SIN_FIRMA;
        }
        return tokenOriginal.substring(0, tokenOriginal.lastIndexOf('.') + 1) + FIRMA_ALTERADA;
    }
}
