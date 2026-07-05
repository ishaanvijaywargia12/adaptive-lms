package com.lms.module.analytics.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;
import java.util.UUID;

@Configuration
@Slf4j
public class WeeklyReportBatchConfig {

    @Bean
    public Job weeklyInstructorReportJob(JobRepository jobRepository, Step generateReportStep) {
        return new JobBuilder("weeklyInstructorReportJob", jobRepository)
                .start(generateReportStep)
                .build();
    }

    @Bean
    public Step generateReportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("generateReportStep", jobRepository)
                .<UUID, String>chunk(10, transactionManager)
                .reader(instructorReader())
                .processor(reportProcessor())
                .writer(emailWriter())
                .build();
    }

    @Bean
    public ItemReader<UUID> instructorReader() {
        // Placeholder: would fetch all distinct instructor IDs across tenants
        return new ListItemReader<>(Collections.singletonList(UUID.randomUUID()));
    }

    @Bean
    public ItemProcessor<UUID, String> reportProcessor() {
        return instructorId -> {
            log.info("Generating weekly report PDF for instructor: {}", instructorId);
            // In a full implementation, this calls AnalyticsService to build the report
            // and iText to generate the PDF byte array or MinIO URL
            return "Report for " + instructorId;
        };
    }

    @Bean
    public ItemWriter<String> emailWriter() {
        return reports -> {
            for (String report : reports) {
                log.info("Sending weekly report email: {}", report);
                // Calls Spring Mail service with the generated PDF attachment
            }
        };
    }
}
