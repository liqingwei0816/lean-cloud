package com.producer;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TestController {

    @Resource
    private ApplicationContext applicationContext;



    @RequestMapping("sayHi")
    public String sayHi(@RequestBody Map<String,String> name){
        return "Hi  "+name.get("name");
    }
    @RequestMapping("sayHello")
    public String sayHi1(String name){
        String property = applicationContext.getEnvironment().getProperty("gateway.version", String.class);

        return "Hi provider "+name +"---"+property;
    }
    @RequestMapping("config")
    public Map<String, Object> config(String name){
        Map<String, Object> properties = new HashMap<>();
        String userName = applicationContext.getEnvironment().getProperty("user.name");
        Integer age = applicationContext.getEnvironment().getProperty("user.age",Integer.class);
        properties.put("userName",userName);
        properties.put("age",age);
        return properties;
    }


}
