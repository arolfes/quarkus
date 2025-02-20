package io.quarkus.redis.client.deployment.devmode;

import java.util.function.Function;
import java.util.function.Supplier;

import org.hamcrest.Matchers;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.redis.client.deployment.RedisTestResource;
import io.quarkus.test.QuarkusDevModeTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.restassured.RestAssured;

@QuarkusTestResource(RedisTestResource.class)
public class RedisClientDevModeTestCase {

    @RegisterExtension
    static QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(new Supplier<>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addAsResource(new StringAsset("quarkus.redis.hosts=${quarkus.redis.tr}"),
                                    "application.properties")
                            .addClass(IncrementResource.class);
                }
            });

    @Test
    public void testRedisDevMode() {
        RestAssured.get("/inc")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("1"));
        RestAssured.get("/inc")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("2"));
        test.modifySourceFile(IncrementResource.class, new Function<String, String>() {
            @Override
            public String apply(String s) {
                return s.replace("INCREMENT = 1", "INCREMENT = 10");
            }
        });
        RestAssured.get("/inc")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("12"));
        RestAssured.get("/inc")
                .then()
                .statusCode(200)
                .body(Matchers.equalTo("22"));

    }
}
