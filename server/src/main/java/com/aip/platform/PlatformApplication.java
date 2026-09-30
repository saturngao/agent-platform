package com.aip.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 工作流编排与智能体平台 - 启动类
 *
 * @author WorkBuddy
 */
@EnableAsync
@EnableScheduling
@MapperScan("com.aip.platform.**.mapper")
@SpringBootApplication
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
        System.out.println("\n" +
                "   ___    ____                    _                 _   _\n" +
                "  / _ \\  |  _ \\   _ __     __ _  | |_   ___   _ __ | |_| |\n" +
                " / /_\\ \\ | |_) | | '_ \\   / _` | | __| / _ \\ | '__|| __|\n" +
                "|  __  | |  __/  | |_) | | (_| | | |_ |  __/ | |   | |_\n" +
                "|_| |_/  |_|     | .__/   \\__,_|  \\__| \\___| |_|    \\__|\n" +
                "                 |_|        AI Workflow & Agent Platform\n" +
                "  Docs : http://localhost:8088/api/health\n" +
                "---------------------------------------------------------------\n");
    }
}
