package com.producer;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @RequestMapping("sayHi")
    public String sayHi(String name){
        return "Hi "+name;
    }
    @RequestMapping("provider/sayHi")
    public String sayHi1(String name){
        return "Hi provider "+name;
    }


}
