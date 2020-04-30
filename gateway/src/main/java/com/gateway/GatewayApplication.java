package com.gateway;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Properties;
import java.util.concurrent.Executor;

@SpringBootApplication
@EnableDiscoveryClient
@RestController
@Slf4j
public class GatewayApplication {

    //熔断反馈
    @RequestMapping("/fallback")
    public Mono<String> fallback() {
        // Mono是一个Reactive stream，对外输出一个“fallback”字符串。
        return Mono.just("系统繁忙");
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext application = SpringApplication.run(GatewayApplication.class, args);

        String serverAddr =application.getEnvironment().getProperty("spring.cloud.nacos.config.server-addr");
        String dataId = "gateway";
        String group = application.getEnvironment().getProperty("spring.cloud.nacos.config.group");
        Properties properties = new Properties();
        properties.put("serverAddr", serverAddr);
        try {
            ConfigService configService = NacosFactory.createConfigService(properties);
            String content = configService.getConfig(dataId, group, 5000);
            configService.addListener(dataId, group, new Listener() {
                @Override
                public void receiveConfigInfo(String configInfo) {
                    System.out.println("recieve1:" + configInfo);
                }
                @Override
                public Executor getExecutor() {
                    return null;
                }
            });
            System.out.println(content);
        } catch (NacosException e) {
            log.error("Nacos配置监听失败",e);
        }


    }





   /* @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(p ->
                    p.path("/**").filters(f->f.stripPrefix(1)).uri("lb://**")
                ).build();
                // 配制一个路由,把 http://网关地址:网关端口/demo/ 下的请求路由到 demo-service 微服务中
               *//* .route(p -> p
                       // .path("/provider/**")
                        .path("/provider/**")
                        .filters(f -> f
                                *//**//* gateway默认不会移除服务名
                                 zuul 中, 例如我们配制的是 把 /demo/** 路由到 http://服务/, 则网关的请求地址: http://网关/demo/xx/abc.do
                                 实际请求的服务地址为: http://服务/xx/abc.do, zuul自动把 /demo 去掉了.
                                    而 Spring Cloud Gateway 不是这样:在Spring Cloud Gateway中,
                                上面的实际请求的服务地址为: http://服务/demo/xx/abc.do ,
                                Spring Cloud Gateway不会把 /demo 去掉,与请求的地址一样
                                 *//**//*

                                .stripPrefix(1)
                                .hystrix(config -> config   // 对path()指定的请求使用熔断器
                                        .setName("mycmd")   // 熔断器的名字
                                        .setFallbackUri("forward:/fallback"))
                                        )  // 熔断到 /fallback, 就是上面配制的那个
                        .uri("lb://provider"))  // 将请求路由到指定目标, lb开头是注册中心中的服务
                .build();*//*
    }*/

}
