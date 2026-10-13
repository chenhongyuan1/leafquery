package com.example.leafquery.controller;

import com.example.leafquery.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import java.util.Map;

/** Compatibility bridge for the two large legacy controllers; raw domain protocols stay intact. */
@RestControllerAdvice
public class LegacyEnvelopeAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        Class<?> owner = method.getContainingClass();
        return (AdminController.class.isAssignableFrom(owner) || DiscoveryController.class.isAssignableFrom(owner))
                && MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
                                 Class<? extends HttpMessageConverter<?>> converter,
                                 ServerHttpRequest request, ServerHttpResponse response) {
        return body instanceof Map<?, ?> map && map.get("code") instanceof Number
                ? ApiResponse.fromLegacy(map) : body;
    }
}
