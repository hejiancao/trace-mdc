# springboot+mdc实现链路追踪

# 代码托管地址

[https://github.com/hejiancao/trace-mdc](https://github.com/hejiancao/trace-mdc)

# 实现过程

新建工程

service-a：服务调用方

service-b：服务提供方

service-common：公共代码，后期可以打成jar包形式

说明：考虑到很多代码是公共的，把公共代码抽取到common服务中，可以把common做成jar包形式。

a，b服务依赖common，需要使用里面的bean，因为服务启动时只会扫描启动类所在的包及其子包，因此需要配置扫描路径才能扫描到common服务里的bean，可以使用`@ComponentScan(basePackages = {"com.example"})`

实现思路：

- 通过拦截器拦截请求，在header中获取traceId的值，如果没有则生成并保存到mdc中
- 请求完成清楚mdc中的值，防止内存溢出

```java
@Component
public class LogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String traceId = Optional.ofNullable(request.getHeader(Constant.TRACE_ID)).orElse(TraceIdUtil.getTraceId());
        MDC.put(Constant.TRACE_ID, traceId);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        MDC.remove(Constant.TRACE_ID);
    }
}
```

并注册拦截器

```java
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private LogInterceptor logInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(logInterceptor);
    }
}
```

```java
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <springProperty scope="context" name="springAppName" source="spring.application.name"/>
    <property name="CONSOLE_LOG_PATTERN"
              value="[TRACEID:%X{trace_id}] %date{yyyy-MM-dd HH:mm:ss.SSS}|${springAppName}|%level|%thread|%clr(%-40.40logger{39})|%line:%message%n"/>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <root level="info">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

注意%X{trance_id}的值要和MDC的键值一致。

## ****子线程日志打印丢失traceId****

```java

/**
 * 解决子线程日志打印丢失traceId问题
 * Created by shaosen on 2022/3/11
 */
public class ThreadPoolExecutorMdcWrapper  extends ThreadPoolExecutor {
    public ThreadPoolExecutorMdcWrapper(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public ThreadPoolExecutorMdcWrapper(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ThreadPoolExecutorMdcWrapper(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ThreadPoolExecutorMdcWrapper(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    public void execute(Runnable task) {
        super.execute(ThreadMdcUtil.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return super.submit(ThreadMdcUtil.wrap(task, MDC.getCopyOfContextMap()), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return super.submit(ThreadMdcUtil.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(ThreadMdcUtil.wrap(task, MDC.getCopyOfContextMap()));
    }
}
```

## **HTTP调用丢失traceId**

```java
public class RestTemplateTraceIdInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(HttpRequest httpRequest, byte[] bytes, ClientHttpRequestExecution clientHttpRequestExecution) throws IOException {
        String traceId = MDC.get(Constant.TRACE_ID);
        if (traceId != null) {
            httpRequest.getHeaders().add(Constant.TRACE_ID, traceId);
        }

        return clientHttpRequestExecution.execute(httpRequest, bytes);
    }
}
```

```java
@Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(Arrays.asList(new RestTemplateTraceIdInterceptor()));
        return restTemplate;
    }
```

## Feign调用丢失traceId

```java
@Configuration
public class FeignRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate requestTemplate) {
        String traceId = MDC.get(Constant.TRACE_ID);
        if (traceId != null) {
            requestTemplate.header(Constant.TRACE_ID, traceId);
        }
    }
}
```

但是这种方式有一个问题，feign调用其实是新建了一个线程，我们知道mdc底层实际上是threadLocal，那么既然不是同一线程必然取不到值，这是我们需要解决的问题。

解决方案：使用`RequestHeader` 传递traceId

```java
//使用RequestHeader传递traceId
@GetMapping(value = "/user")
String user(@RequestHeader(Constant.TRACE_ID) String traceId);
```

```java
@GetMapping("/feign")
public void feignTest() {
    String user = serviceBFeign.user(MDC.get(Constant.TRACE_ID));
    log.info(user);
}
```

或者`feign.hystrix.enabled=false` ，如果不开启熔断，使用拦截器FeignRequestInterceptor是可以获取mdc值的，但是不建议这样做。

参考:

[https://mp.weixin.qq.com/s/lWpyAH_gC1zJIOe8oLPuyw](https://mp.weixin.qq.com/s/lWpyAH_gC1zJIOe8oLPuyw)

[https://cloud.tencent.com/developer/article/1621309](https://cloud.tencent.com/developer/article/1621309)

[https://blog.csdn.net/hkk666123/article/details/113964715](https://blog.csdn.net/hkk666123/article/details/113964715)

[https://www.cnblogs.com/baizhanshi/p/11176343.html](https://www.cnblogs.com/baizhanshi/p/11176343.html)