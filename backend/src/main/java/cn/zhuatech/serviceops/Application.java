// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 服务运营应用与周期维护任务入口。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@SpringBootApplication
@EnableScheduling
public class Application {
  /** 启动企业服务业务。官网 https://www.zhuatech.cn/，商业咨询微信 zhuatech / zhuatech2。 */
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
