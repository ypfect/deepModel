package com.deepmodel.relation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.deepmodel.relation.dao")
public class RelationApplication {
    public static void main(String[] args) {
        SpringApplication.run(RelationApplication.class, args);
    }
}
