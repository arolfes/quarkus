package io.quarkus.opentelemetry.runtime.tracing.cdi;

import static io.quarkus.opentelemetry.runtime.config.OpenTelemetryConfig.INSTRUMENTATION_NAME;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.quarkus.arc.DefaultBean;
import io.quarkus.opentelemetry.runtime.tracing.DelayedAttributes;
import io.quarkus.opentelemetry.runtime.tracing.LateBoundSampler;

@Singleton
public class TracerProducer {
    @Produces
    @Singleton
    public DelayedAttributes getDelayedAttributes() {
        return new DelayedAttributes();
    }

    @Produces
    @Singleton
    public LateBoundSampler getLateBoundSampler() {
        return new LateBoundSampler();
    }

    @Produces
    @Singleton
    @DefaultBean
    public Tracer getTracer() {
        return GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
    }
}
