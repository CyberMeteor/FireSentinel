package com.firesentinel.alarmsystem.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for distributed tracing using OpenTelemetry.
 */
@Configuration
public class TracingConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${opentelemetry.jaeger.endpoint:http://localhost:14250}")
    private String jaegerEndpoint;

    /**
     * Creates and configures the OpenTelemetry SDK.
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        // Configure the Jaeger exporter
        JaegerGrpcSpanExporter jaegerExporter = JaegerGrpcSpanExporter.builder()
                .setEndpoint(jaegerEndpoint)
                .build();

        // Define attribute keys
        AttributeKey<String> serviceNameKey = AttributeKey.stringKey("service.name");
        AttributeKey<String> serviceVersionKey = AttributeKey.stringKey("service.version");

        // Configure the tracer provider
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(jaegerExporter).build())
                .setResource(Resource.create(Attributes.of(
                        serviceNameKey, applicationName,
                        serviceVersionKey, "1.0.0"
                )))
                .setSampler(Sampler.alwaysOn())
                .build();

        // Build the OpenTelemetry SDK
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    /**
     * Creates a tracer for the application.
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(applicationName);
    }
} 