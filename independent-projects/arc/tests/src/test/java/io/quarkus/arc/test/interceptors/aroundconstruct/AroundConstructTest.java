package io.quarkus.arc.test.interceptors.aroundconstruct;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Singleton;
import javax.interceptor.AroundConstruct;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.test.ArcTestContainer;

public class AroundConstructTest {

    @RegisterExtension
    public ArcTestContainer container = new ArcTestContainer(MyTransactional.class, SimpleBean.class,
            SimpleInterceptor.class);

    public static AtomicBoolean INTERCEPTOR_CALLED = new AtomicBoolean(false);

    @Test
    public void testInterception() {
        SimpleBean simpleBean = Arc.container().instance(SimpleBean.class).get();
        assertNotNull(simpleBean);
        Assertions.assertTrue(INTERCEPTOR_CALLED.get());
    }

    @Singleton
    @MyTransactional
    static class SimpleBean {

    }

    @MyTransactional
    @Interceptor
    public static class SimpleInterceptor {

        @AroundConstruct
        void mySuperCoolAroundConstruct(InvocationContext ctx) throws Exception {
            INTERCEPTOR_CALLED.set(true);
            ctx.proceed();
        }

    }
}
