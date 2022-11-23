package com.example.resources;

import com.example.models.Fruit;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;

import static javax.ws.rs.core.Response.Status.CREATED;

@Path("/fruits")
@ApplicationScoped
@Produces("application/json")
@Consumes("application/json")
public class FruitResource {

    @GET
    public Uni<List<Fruit>> get(){
        return Fruit.listAll(Sort.by("name"));
    }

    @GET
    @Path("{id}")
    public Uni<Fruit> getSingle(Long id){
        return Fruit.findById(id);
    }

    @POST
    public Uni<Response> create(Fruit fruit){

        if(fruit == null || fruit.id != null){
            throw new WebApplicationException("Id was invadidly set on request.", 422);
        }

        return Panache.withTransaction(fruit::persist)
                .replaceWith(Response.ok(fruit).status(CREATED)::build);
    }

}
