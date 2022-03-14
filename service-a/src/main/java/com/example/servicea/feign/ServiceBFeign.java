package com.example.servicea.feign;

import com.example.servicecommon.config.Constant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Created by shaosen on 2022/3/14
 */

@FeignClient(value = "service-b", fallback = ServiceBFeign.ServiceBFeignHystrix.class)
public interface ServiceBFeign {

    //使用RequestHeader传递traceId
    @GetMapping(value = "/user")
    String user(@RequestHeader(Constant.TRACE_ID) String traceId);

    @Slf4j
    @Component
    class ServiceBFeignHystrix implements ServiceBFeign {
        @Override
        public String user(String traceId) {
            return "service-b请求异常，开启熔断";
        }
    }

}
