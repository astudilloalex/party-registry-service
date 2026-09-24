package com.alexastudillo.partyregistry.architecture.fixture.isolation.domain;

import com.alexastudillo.api.response.contract.ApiResponse;

import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.http.HttpResponse;

/**
 * Represents forbidden Domain dependencies on REST and HTTP response contracts.
 */
public final class DomainDependingOnHttpContracts {

    public ApiResponse<String> apiResponse(ApiResponse<String> response) {
        return response;
    }

    public Response jakartaResponse(Response response) {
        return response;
    }

    public HttpResponse<String> javaHttpResponse(HttpResponse<String> response) {
        return response;
    }

    public RestResponse<String> restResponse(RestResponse<String> response) {
        return response;
    }
}
