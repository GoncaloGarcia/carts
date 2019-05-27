package works.weave.socks.cart;

import com.feedzai.commons.tracing.engine.JaegerTracingEngine;
import com.feedzai.commons.tracing.engine.TraceUtil;
import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.time.Duration;

@SpringBootApplication
@EnablePrometheusEndpoint
public class CartApplication {

    public static void main(String[] args) {
        TraceUtil.init(new JaegerTracingEngine.Builder().withSampleRate(1).withCacheMaxSize(10000).withCacheDuration(Duration.ofDays(2)).withProcessName("Cart Service").withIp("172.31.0.10").build());
        SpringApplication.run(CartApplication.class, args);
    }
}
