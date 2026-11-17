package com.utp.trazalab.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.utp.trazalab.controller.IncidenciaWebController;
import com.utp.trazalab.dto.RegistroIncidenciaFormulario;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler manejador = new GlobalExceptionHandler();

    @Test
    void responde404CuandoNoExisteElRecurso() {
        ResponseEntity<ProblemDetail> respuesta = manejador.manejarRecursoNoEncontrado(
                new RecursoNoEncontradoException("No existe la computadora."));

        assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
        assertEquals("Recurso no encontrado", respuesta.getBody().getTitle());
        assertEquals("No existe la computadora.", respuesta.getBody().getDetail());
    }

    @Test
    void responde409CuandoLaOperacionEntraEnConflictoConElDominio() {
        ResponseEntity<ProblemDetail> respuesta = manejador.manejarConflictoNegocio(
                new ConflictoNegocioException("El componente ya está instalado."));

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals("Conflicto de negocio", respuesta.getBody().getTitle());
    }

    @Test
    void responde400CuandoLaSolicitudNoCumpleUnaRegla() {
        ResponseEntity<ProblemDetail> respuesta = manejador.manejarSolicitudInvalida(
                new IllegalArgumentException("El periodo no es válido."));

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertEquals("El periodo no es válido.", respuesta.getBody().getDetail());
    }

    @Test
    void agrupaErroresDeValidacionPorCampo() throws Exception {
        Method metodo = IncidenciaWebController.class.getDeclaredMethod(
                "registrar",
                RegistroIncidenciaFormulario.class,
                BindingResult.class,
                Model.class,
                RedirectAttributes.class);
        MethodParameter parametro = new MethodParameter(metodo, 0);
        BeanPropertyBindingResult resultado = new BeanPropertyBindingResult("", "registroIncidencia");
        resultado.addError(new FieldError("registroIncidencia", "codigoQr", "Ingresa el QR."));
        MethodArgumentNotValidException error = new MethodArgumentNotValidException(parametro, resultado);

        ResponseEntity<ProblemDetail> respuesta = manejador.manejarValidacion(error);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertEquals("Solicitud inválida", respuesta.getBody().getTitle());
        Map<?, ?> errores = assertInstanceOf(Map.class, respuesta.getBody().getProperties().get("errores"));
        assertEquals(List.of("Ingresa el QR."), errores.get("codigoQr"));
    }

    @Test
    void noExponeDetallesDeViolacionDeIntegridadDeLaBase() {
        ResponseEntity<ProblemDetail> respuesta = manejador.manejarConflictoPersistencia(
                new DataIntegrityViolationException("detalle interno de PostgreSQL"));

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertEquals("La operación entra en conflicto con datos existentes.", respuesta.getBody().getDetail());
    }

}
