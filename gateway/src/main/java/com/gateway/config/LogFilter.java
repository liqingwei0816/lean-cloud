package com.gateway.config;

import io.netty.buffer.UnpooledByteBufAllocator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * 日志打印过滤器
 */
@Component
public class LogFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        URI requestUri = request.getURI();
        HttpHeaders headers = request.getHeaders();
        MediaType mediaType = headers.getContentType();
        //日志对象实体
        LogEntity logEntity = new LogEntity();
        //记录请求类型
        logEntity.setMethod(request.getMethod());
        //记录请求路径
        logEntity.setUrl(request.getURI().getPath());
        // 排除流文件类型,比如上传的文件contentType.contains("multipart/form-data")
        if (Objects.nonNull(mediaType) && mediaType.getType().contains("multipart/form-data")) {
            logEntity.setRequestBody("上传文件");
        }
        //get方式直接获取请求参数
        if (HttpMethod.GET.equals(request.getMethod())) {
            //记录请求参数
            logEntity.setRequestBody(requestUri.getQuery());
        }
        //其他方式，获取请求体
        if (!HttpMethod.GET.equals(request.getMethod()) && headers.getContentLength() > 0) {
            ServerRequest serverRequest = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
            return serverRequest.bodyToMono(String.class).flatMap(body -> {
                //记录请求体
                logEntity.setRequestBody(body);
                ServerHttpRequest serverHttpRequestDecorator = RewriteUtil.rewriteRequest(request, logEntity);
                ServerHttpResponse serverHttpResponse = RewriteUtil.rewriteResponse(exchange, logEntity);
                return chain.filter(exchange.mutate().request(serverHttpRequestDecorator).response(serverHttpResponse).build());
            });
        }
        //重写Response对象，并打印日志
        ServerHttpResponse serverHttpResponse = RewriteUtil.rewriteResponse(exchange, logEntity);
        return chain.filter(exchange.mutate().response(serverHttpResponse).build());

    }

    /**
     * 重写请求对象和响应对象的工具类
     */
    @Slf4j(topic = "httpLog")
    private static class RewriteUtil {
        /**
         * 读取请求流并重写请求对象
         */
        static ServerHttpRequest rewriteRequest(ServerHttpRequest request, LogEntity logEntity) {
            // 重写原始请求
            return new ServerHttpRequestDecorator(request) {
                @Override
                @NonNull
                public Flux<DataBuffer> getBody() {
                    NettyDataBufferFactory nettyDataBufferFactory = new NettyDataBufferFactory(new UnpooledByteBufAllocator(false));
                    DataBuffer bodyDataBuffer = nettyDataBufferFactory.wrap(logEntity.getRequestBody().getBytes());
                    return Flux.just(bodyDataBuffer);
                }
            };
        }

        /**
         * 读取响应流并重写响应对象
         */
        static ServerHttpResponse rewriteResponse(ServerWebExchange exchange, LogEntity logEntity) {
            // 获取response的返回数据
            ServerHttpResponse originalResponse = exchange.getResponse();
            logEntity.setStatus(originalResponse.getStatusCode());
            // 封装返回体
            return new ServerHttpResponseDecorator(originalResponse) {
                @Override
                @NonNull
                public Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                    if (body instanceof Flux) {
                        Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                        return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                            DataBuffer join = bufferFactory.join(dataBuffers);
                            String respBody = join.toString(0, join.readableByteCount(), Charset.defaultCharset());
                            //记录响应数据
                            logEntity.setResponseBody(respBody);
                            //打印日志
                            if (log.isDebugEnabled()){
                                log.debug(logEntity.toString());
                            }

                            return bufferFactory.wrap(respBody.getBytes());
                        }));
                    }
                    return super.writeWith(body);
                }
            };
        }

    }

    /**
     * 日志实体类
     */
    @Getter
    @Setter
    private static class LogEntity {
        private String url;
        private HttpMethod method;
        private String requestBody;
        private String responseBody;
        private HttpStatus status;

        @Override
        public String toString() {
            return "{\n" + url + '\t' + method.name() + "\t" + status.value() +
                    "\nreq: " + requestBody +
                    "\nres " + responseBody + "\n}";

        }
    }
}

