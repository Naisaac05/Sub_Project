package com.devmatch.service;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class JitsiMeetService {
    private static final String JITSI_BASE_URL = "https://meet.jit.si";

    public String generateMeetLink(Long matchingId, LocalDate sessionDate) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String roomName = String.format("devmatch-%d-%s-%s",
                matchingId,
                sessionDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                uuid);
        return JITSI_BASE_URL + "/" + roomName;
    }
}
