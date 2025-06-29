package dev.brayanmnz.dockermodelrunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DockerModelRunnerApplication {

    Logger logger = LoggerFactory.getLogger(DockerModelRunnerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DockerModelRunnerApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(ChatClient.Builder builder) {
        return args -> {
            var res = builder.build()
                    .prompt("When was Java created?")
                    .call()
                    .content();

            logger.info(res);
        };
    }
}
