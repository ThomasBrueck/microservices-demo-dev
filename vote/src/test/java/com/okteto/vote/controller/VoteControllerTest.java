package com.okteto.vote.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletResponse;

class VoteControllerTest {

    @Test
    void postFormUsesStableVoterIdAsKafkaKey() {
        VoteController controller = new VoteController();
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);

        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("expected in unit test"));
        when(kafkaTemplate.send(eq("votes"), anyString(), anyString())).thenReturn(failedFuture);

        ReflectionTestUtils.setField(controller, "kafkaTemplate", kafkaTemplate);

        VoteController.Vote voteInput = new VoteController.Vote();
        voteInput.setVote("a");
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.postForm("user-123", voteInput, model, response);

        verify(kafkaTemplate).send("votes", "user-123", "a");
        assertThat(response.getCookie("voter_id")).isNotNull();
        assertThat(response.getCookie("voter_id").getValue()).isEqualTo("user-123");
    }

    @Test
    void postFormGeneratesCookieAndUsesGeneratedIdWhenMissing() {
        VoteController controller = new VoteController();
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);

        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("expected in unit test"));
        when(kafkaTemplate.send(eq("votes"), anyString(), anyString())).thenReturn(failedFuture);

        ReflectionTestUtils.setField(controller, "kafkaTemplate", kafkaTemplate);

        VoteController.Vote voteInput = new VoteController.Vote();
        voteInput.setVote("b");
        Model model = new ExtendedModelMap();
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.postForm("", voteInput, model, response);

        assertThat(response.getCookie("voter_id")).isNotNull();
        String generatedId = response.getCookie("voter_id").getValue();
        assertThat(generatedId).isNotBlank();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("votes"), keyCaptor.capture(), eq("b"));
        assertThat(keyCaptor.getValue()).isEqualTo(generatedId);
    }
}
