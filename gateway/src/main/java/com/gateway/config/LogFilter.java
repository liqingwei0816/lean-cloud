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
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
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
        LogEntity logEntity = new LogEntity();
        logEntity.setMethod(request.getMethod());
        logEntity.setUrl(request.getURI().getPath());
        // 排除流文件类型,比如上传的文件contentType.contains("multipart/form-data")
        if (Objects.nonNull(mediaType) && mediaType.getType().contains("multipart/form-data")) {
            logEntity.setRequestBody("上传文件");
            return chain.filter(exchange);
        }
        if (HttpMethod.GET.equals(request.getMethod())) {
            logEntity.setRequestBody(requestUri.getQuery());
            return chain.filter(exchange);
        }
        if (headers.getContentLength() > 0) {
            ServerRequest serverRequest = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
           return serverRequest.bodyToMono(String.class).flatMap(o->{
                logEntity.setRequestBody(o);
                // 重写原始请求
               ServerHttpRequestDecorator serverHttpRequestDecorator = new ServerHttpRequestDecorator(request) {
                   @Override
                   @NonNull
                   public Flux<DataBuffer> getBody() {
                       NettyDataBufferFactory nettyDataBufferFactory = new NettyDataBufferFactory(new UnpooledByteBufAllocator(false));
                       DataBuffer bodyDataBuffer = nettyDataBufferFactory.wrap(logEntity.getRequestBody().getBytes());
                       return Flux.just(bodyDataBuffer);
                   }
               };
               ServerHttpResponse serverHttpResponse = RewriteUtil.rewriteResponse(exchange, logEntity);
               return chain.filter(exchange.mutate().request(serverHttpRequestDecorator).response(serverHttpResponse).build());
           });





        } else {
            return chain.filter(exchange);
        }

    }





  /*  @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            URI requestUri = request.getURI();
            String schema = requestUri.getScheme();

            // 只记录http、https请求
            if ((!"http".equals(schema) && !"https".equals(schema))) {
                return chain.filter(exchange);
            }
            //日志对象
            LogEntity logEntity = new LogEntity();
            logEntity.setMethod(request.getMethod());
            logEntity.setUrl(request.getURI().getPath());
            //请求体只能读一次,获取后重写请求对象
            ServerHttpRequest serverHttpRequest = RewriteUtil.rewriteRequest(exchange, logEntity);
            //响应体只能读一次,获取后重写响应对象
            ServerHttpResponse serverHttpResponse = RewriteUtil.rewriteResponse(exchange, logEntity);

            return chain.filter(exchange.mutate().request(serverHttpRequest).response(serverHttpResponse).build());

        } catch (Exception e) {
            log.error("请求响应日志打印出现异常", e);
            return chain.filter(exchange);
        }

    }
*/

    /**
     * 重写请求对象和响应对象的工具类
     */
    @Slf4j(topic = "httpLog")
    private static class RewriteUtil {
        /**
         * 读取请求流并重写请求对象
         */
        static ServerHttpRequest rewriteRequest(ServerWebExchange exchange, LogEntity logEntity) {
            ServerHttpRequest request = exchange.getRequest();
            URI requestUri = request.getURI();
            HttpHeaders headers = request.getHeaders();
            MediaType mediaType = headers.getContentType();

            // 排除流文件类型,比如上传的文件contentType.contains("multipart/form-data")
            if (Objects.nonNull(mediaType) && mediaType.getType().contains("multipart/form-data")) {
                logEntity.setRequestBody("上传文件");
                return request;
            }
            if (HttpMethod.GET.equals(request.getMethod())) {
                logEntity.setRequestBody(requestUri.getQuery());
                return request;
            }
            if (headers.getContentLength() > 0) {
                ServerRequest serverRequest = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
                AtomicReference<String> reqBody = new AtomicReference<>();
                logEntity.setRequestBody(reqBody.get());
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
            } else {
                return request;
            }
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
                            logEntity.setResponseBody(respBody);
                            log.error(logEntity.toString());
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
        /*log.info("url:{},method:{},请求内容:{},响应内容:{},status:{}",
       requestUri, method, requestBody.get(), responseBody.get(), serverHttpResponse.getStatusCode());*/
        private String url;
        private HttpMethod method;
        private String requestBody;
        private String responseBody;
        private HttpStatus status;

        @Override
        public String toString() {
            return "{\n" + url + '\t' + method.name() + "\t" + status.value() +
                    "\nrequestBody:\n" + requestBody +
                    "\nresponseBody\n" + responseBody + "}";

        }
    }
}

