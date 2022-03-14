package com.example.servicea;

import com.example.servicea.feign.ServiceBFeign;
import com.example.servicecommon.config.Constant;
import com.example.servicecommon.config.ThreadPoolExecutorMdcWrapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@EnableFeignClients
@EnableDiscoveryClient
@ComponentScan(basePackages = {"com.example"})
@Slf4j
@RestController
@SpringBootApplication
public class ServiceAApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAApplication.class, args);
    }

    @Resource
    private RestTemplate restTemplate;
    @Resource
    private ServiceBFeign serviceBFeign;

    @GetMapping("/hello")
    public void hello() {
        log.info("------>springboot+mdc实现链路追踪");
    }

    @GetMapping("/tread")
    public void hello2() {
        //ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(5, 10, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        //解决子线程日志打印traceId丢失，重写线程池
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutorMdcWrapper(5, 10, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        for (int i = 0; i < 5; i++) {
            threadPoolExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    log.info("------>线程{}执行了", Thread.currentThread().getName());
                }
            });
        }
        log.info("------>任务执行完毕");

    }

    @GetMapping("/http")
    public void hello3() {
        ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:1002/user", String.class);
        log.info(response.getBody());
    }

    @GetMapping("/feign")
    public void feignTest() {
        String user = serviceBFeign.user(MDC.get(Constant.TRACE_ID));
        log.info(user);
    }

}
