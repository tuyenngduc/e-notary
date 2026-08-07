package com.actvn.enotary.controller;

import com.actvn.enotary.dto.response.SignVideoDocumentResponse;
import com.actvn.enotary.dto.response.VideoSessionResponse;
import com.actvn.enotary.entity.User;
import com.actvn.enotary.enums.Role;
import com.actvn.enotary.enums.RequestStatus;
import com.actvn.enotary.security.CustomUserDetails;
import com.actvn.enotary.service.VideoSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VideoSessionControllerTest {

    private MockMvc mockMvc;
    private VideoSessionService videoSessionService;

    @BeforeEach
    void setUp() {
        videoSessionService = Mockito.mock(VideoSessionService.class);
        VideoSessionController controller = new VideoSessionController(videoSessionService);
        ReflectionTestUtils.setField(controller, "frontendBaseUrl", "http://localhost:5173");

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.actvn.enotary.exception.GlobalExceptionHandler())
                .build();
    }

    @Test
    void getVideoRoom_withToken_redirectsToFrontendAndPassesToken() throws Exception {
        when(videoSessionService.verifySessionToken("token-123"))
                .thenReturn(VideoSessionResponse.builder().roomId("room_abcd").build());

        mockMvc.perform(get("/api/video/room/room_abcd").param("token", "token-123"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/video/room/room_abcd?token=token-123"));

        verify(videoSessionService, times(1)).verifySessionToken("token-123");
    }

    @Test
    void getVideoRoom_withoutToken_redirectsToFrontendWithoutToken() throws Exception {
        mockMvc.perform(get("/api/video/room/room_xyz"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:5173/video/room/room_xyz"));

        verifyNoInteractions(videoSessionService);
    }

    @Test
    void signDocument_authenticatedUser_callsServiceAndReturnsStatus() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setEmail("client@example.com");
        user.setRole(Role.CLIENT);
        user.setIsActive(true);
        CustomUserDetails principal = new CustomUserDetails(user);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(videoSessionService.signDocument(
                eq(sessionId),
                eq("client@example.com"),
                eq(documentId),
                eq("signature"),
                eq(1),
                eq(10.0),
                eq(70.0),
                eq(25.0),
                eq(10.0)
        ))
                .thenReturn(SignVideoDocumentResponse.builder()
                        .clientSigned(true)
                        .notarySigned(false)
                        .completed(false)
                        .requestStatus(RequestStatus.IN_VIDEO_CALL)
                        .build());

        mockMvc.perform(post("/api/video/sessions/{id}/signatures", sessionId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":\"" + documentId + "\",\"signatureValue\":\"signature\","
                                + "\"pageNumber\":1,\"xPercent\":10,\"yPercent\":70,\"widthPercent\":25,\"heightPercent\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientSigned").value(true))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.requestStatus").value("IN_VIDEO_CALL"));

        verify(videoSessionService).signDocument(sessionId, "client@example.com", documentId, "signature", 1, 10.0, 70.0, 25.0, 10.0);
    }
}

