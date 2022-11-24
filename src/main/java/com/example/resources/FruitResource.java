package com.example.resources;

import com.example.models.Fruit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.CompositeException;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.List;

import static javax.ws.rs.core.Response.Status.*;

/**
 * Clase que actúa como interfaz REST (Spring REST Controller)
 */
@Path("/fruits")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class FruitResource {

    private static final Logger LOGGER = Logger.getLogger(FruitResource.class.getName());

    /**
     * Recupera una lista de frutas.
     *
     * @return List<Fruit>
     */
    @GET
    public Uni<List<Fruit>> get(){
        return Fruit.listAll(Sort.by("name"));
    }

    /**
     * Recupera una fruta por ID
     *
     * @param id
     * @return Fruit
     */
    @GET
    @Path("{id}")
    public Uni<Fruit> getSingle(Long id){
        return Fruit.findById(id);
    }

    /**
     * Sirve para crear una nueva fruta <strong>OJO: </strong> no se debe pasar el id, sólo el nombre.
     *
     * @param fruit
     * @return HTTP Status
     */
    @POST
    public Uni<Response> create(Fruit fruit){

        if(fruit == null || fruit.id != null){
            throw new WebApplicationException("Id was invadidly set on request.", 422);
        }

        return Panache.withTransaction(fruit::persist)
                .replaceWith(Response.ok(fruit).status(CREATED)::build);
    }

    /**
     * Modifica el nombre de una fruta (por Id)
     *
     * @param id
     * @param fruit
     * @return HTTP Response
     */
    @PUT
    @Path("{id}")
    public Uni<Response> update(Long id, Fruit fruit) {
        if (fruit == null || fruit.name == null) {
            throw new WebApplicationException("Fruit name was not set on request.", 422);
        }

        return Panache
                .withTransaction(() -> Fruit.<Fruit> findById(id)
                        .onItem().ifNotNull().invoke(entity -> entity.name = fruit.name)
                )
                .onItem().ifNotNull().transform(entity -> Response.ok(entity).build())
                .onItem().ifNull().continueWith(Response.ok().status(NOT_FOUND)::build);
    }

    /**
     * Elimina una fruta por ID
     *
     * @param id
     * @return HTTP Response
     */
    @DELETE
    @Path("{id}")
    public Uni<Response> delete(Long id) {
        return Panache.withTransaction(() -> Fruit.deleteById(id))
                .map(deleted -> deleted
                        ? Response.ok().status(NO_CONTENT).build()
                        : Response.ok().status(NOT_FOUND).build());
    }

    /**
     * Create a HTTP response from an exception.
     *
     * Response Example:
     *
     * <pre>
     * HTTP/1.1 422 Unprocessable Entity
     * Content-Length: 111
     * Content-Type: application/json
     *
     * {
     *     "code": 422,
     *     "error": "Fruit name was not set on request.",
     *     "exceptionType": "javax.ws.rs.WebApplicationException"
     * }
     * </pre>
     */
    @Provider
    public static class ErrorMapper implements ExceptionMapper<Exception> {

        @Inject
        ObjectMapper objectMapper;

        @Context
        UriInfo uriInfo;


        /**
         * Exception Handler
         * Cada vez que los métodos fallan y lanzan una excepción, este método intentará interceptar esas excepciones
         * y se retornará una respuesta que puede personalizarse según sea la necesidad.
         *
         * @param exception
         * @return HTTP Response
         */
        @Override
        public Response toResponse(Exception exception) {
            LOGGER.error("Failed to handle request", exception);

            Throwable throwable = exception;

            int code = 500;
            if (throwable instanceof WebApplicationException) {
                code = ((WebApplicationException) exception).getResponse().getStatus();
            }

            // This is a Mutiny exception and it happens, for example, when we try to insert a new
            // fruit but the name is already in the database
            if (throwable instanceof CompositeException) {
                throwable = ((CompositeException) throwable).getCause();
            }

            ObjectNode exceptionJson = objectMapper.createObjectNode();
            exceptionJson.put("exceptionType", throwable.getClass().getName());
            exceptionJson.put("code", code);
            exceptionJson.put("RequestPath", uriInfo.getPath());

            if (exception.getMessage() != null) {
                exceptionJson.put("error", throwable.getMessage());
            }

            return Response.status(code)
                    .entity(exceptionJson)
                    .build();
        }

    }

}
