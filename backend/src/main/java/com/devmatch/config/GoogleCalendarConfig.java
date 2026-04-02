package com.devmatch.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Slf4j
@Configuration
public class GoogleCalendarConfig {

    @Value("${google.calendar.credentials-path:#{null}}")
    private Resource credentialsPath;

    @Value("${google.calendar.application-name:DevMatch}")
    private String applicationName;

    /**
     * Google Calendar API 클라이언트를 빈으로 등록합니다.
     * credentials 파일이 없으면 null을 반환하고,
     * GoogleCalendarService에서 null 체크 후 스텁 모드로 동작합니다.
     */
    @Bean
    public Calendar googleCalendar() {
        if (credentialsPath == null || !credentialsPath.exists()) {
            log.warn("[GoogleCalendar] credentials 파일이 없습니다. 스텁 모드로 동작합니다.");
            return null;
        }

        try {
            GoogleCredentials credentials = GoogleCredentials
                    .fromStream(credentialsPath.getInputStream())
                    .createScoped(Collections.singletonList(CalendarScopes.CALENDAR));

            return new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(applicationName)
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            log.error("[GoogleCalendar] 클라이언트 초기화 실패: {}", e.getMessage());
            return null;
        }
    }
}
