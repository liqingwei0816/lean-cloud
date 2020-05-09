package com.gateway.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionLikeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.event.EnableBodyCachingEvent;
import org.springframework.cloud.gateway.filter.AdaptCachedBodyGlobalFilter;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class NacosGatewayConfig implements ApplicationRunner {

    @Resource
    private RouteDefinitionRepository routeDefinitionRepository;
    @Value("${spring.application.name}")
    private String appName;
    @Resource
    private NacosConfigManager nacosConfigManager;
    @Resource
    private ApplicationEventPublisher publisher;
    @Resource
    private AdaptCachedBodyGlobalFilter adaptCachedBodyGlobalFilter;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String group = nacosConfigManager.getNacosConfigProperties().getGroup();
        String configInfo = nacosConfigManager.getConfigService().getConfigAndSignListener(appName + "Route", group, 5000, new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
                routeConfig(configInfo);
                log.error("配置信息修改：\n" + configInfo);
            }
        });
        routeConfig(configInfo);
        log.error("配置信息初始化：\n" + configInfo);
    }

    @Resource
    private ObjectMapper objectMapper;

    private void routeConfig(String configInfo) {
        CollectionLikeType collectionLikeType = objectMapper.getTypeFactory().constructCollectionLikeType(List.class, RouteDefinition.class);
        try {
            //将配置转换为路由对象
            List<RouteDefinition> routeDefinitions = objectMapper.readValue(configInfo, collectionLikeType);
            //移除已删除的路由信息
            Set<String> idsNew = routeDefinitions.stream().map(RouteDefinition::getId).collect(Collectors.toSet());
            Flux<RouteDefinition> routeDefinitionsNow = routeDefinitionRepository.getRouteDefinitions();
            routeDefinitionsNow.toIterable().forEach(r->{
               publisher.publishEvent(new EnableBodyCachingEvent(adaptCachedBodyGlobalFilter,r.getId()));
                if (!idsNew.contains(r.getId())){
                    routeDefinitionRepository.delete(Mono.just(r.getId())).subscribe();
                }
            });
            //新增和更新存在的路由信息
            routeDefinitions.forEach(r -> routeDefinitionRepository.save(Mono.just(r)).subscribe());

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.error("json解析失败", e);
        }


    }
}
