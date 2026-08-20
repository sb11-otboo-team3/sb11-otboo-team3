package com.otboo.domain.clothes.extraction.service;

import com.otboo.domain.clothes.extraction.exception.InvalidExtractionUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class ExtractionUrlValidator {

    public URI validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidExtractionUrlException(url);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new InvalidExtractionUrlException(url);
        }

        return uri;
    }
}
