package com.ruchira.murex.freemaker;

import com.ruchira.murex.exception.FTLException;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import io.micrometer.core.instrument.util.IOUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class FtlSqlQueryBuilder implements FtlQueryBuilder {
    private static final String UTF_8 = "UTF-8";
    private static final String FTL_RESOURCE_LOCATION = "/ftl/";
    private static final String FTL_QUERY_BUILDER_INIT = "FtlSqlQueryBuilder >> init >> {}";
    private static final Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);
    private final Map<String, Template> ftlCache = new ConcurrentHashMap<>();

    @PostConstruct
    @Override
    public void init() {
        try {
            ClassTemplateLoader classLoader = new ClassTemplateLoader(FtlSqlQueryBuilder.class, FTL_RESOURCE_LOCATION);
            MultiTemplateLoader loader = new MultiTemplateLoader(new TemplateLoader[]{classLoader});
            configuration.setTemplateLoader(loader);
            configuration.setDefaultEncoding(UTF_8);
            configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        } catch (Exception e) {
            log.error(FTL_QUERY_BUILDER_INIT, e.getMessage());
        }
    }

    @Override
    public String buildQuery(Map<String, Object> payload, String ftlFileName) {
        log.info("Generating query using FTL file: {}", ftlFileName);
        try {
            final Template template = ftlCache.computeIfAbsent(ftlFileName, key -> {
                try (InputStream inputStream = FtlSqlQueryBuilder.class.getResourceAsStream(FTL_RESOURCE_LOCATION + key)) {
                    if (inputStream == null) {
                        throw new FileNotFoundException(String.format("Template file not found: %s", key));
                    }
                    String templateContent = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    return new Template(ftlFileName, new StringReader(templateContent), configuration);
                } catch (IOException e) {
                    log.error("Failed to read FTL file {}: {}", key, e.getMessage(), e);
                    throw new UncheckedIOException(e);
                }
            });

            try (Writer out = new StringWriter()) {
                template.process(payload, out);
                return out.toString();
            }
        } catch (Exception exception) {
            String message = String.format("FtlSqlQueryBuilder >> {} >> Can't generate SQL from the FTL %s with the payload %s >>", ftlFileName, payload);
            log.error(message, exception);
            throw new FTLException(message, exception);
        }
    }
}