package daggerok;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class App {

  @Bean
  AsyncTaskExecutor taskExecutor() {
    return new SimpleAsyncTaskExecutor();
  }

  public static void main(String[] args) {
    SpringApplication.run(App.class, args);
  }
}
