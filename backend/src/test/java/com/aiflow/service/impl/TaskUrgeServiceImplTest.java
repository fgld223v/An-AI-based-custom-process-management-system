package com.aiflow.service.impl;

import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.entity.UserEntity;
import com.aiflow.model.ProcessInstance;
import com.aiflow.repository.NotificationRepository;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.security.CurrentUser;
import com.aiflow.service.NotificationService;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class TaskUrgeServiceImplTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void urgesAllParallelTasksAndCandidateUsers() {
        authenticate(10L);
        TaskService taskService = mock(TaskService.class);
        NotificationService notificationService = mock(NotificationService.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        ProcessInstanceRepository instanceRepository = mock(ProcessInstanceRepository.class);
        SysUserRepository userRepository = mock(SysUserRepository.class);
        TaskQuery taskQuery = mock(TaskQuery.class, RETURNS_SELF);
        Task assignedTask = mock(Task.class);
        Task candidateTask = mock(Task.class);
        IdentityLink firstCandidate = mock(IdentityLink.class);
        IdentityLink secondCandidate = mock(IdentityLink.class);

        ProcessInstance instance = ProcessInstance.builder()
                .id(100L)
                .applicantId(10L)
                .title("Role approval")
                .status("running")
                .flowableProcessInstanceId("flowable-100")
                .deleted(0)
                .build();
        when(instanceRepository.findByIdAndDeleted(100L, 0)).thenReturn(Optional.of(instance));
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(assignedTask, candidateTask));
        when(assignedTask.getId()).thenReturn("task-1");
        when(assignedTask.getAssignee()).thenReturn("20");
        when(candidateTask.getId()).thenReturn("task-2");
        when(candidateTask.getAssignee()).thenReturn(null);
        when(firstCandidate.getUserId()).thenReturn("21");
        when(secondCandidate.getUserId()).thenReturn("22");
        when(taskService.getIdentityLinksForTask("task-2"))
                .thenReturn(List.of(firstCandidate, secondCandidate));
        when(notificationService.createNotification(any())).thenReturn(mock(NotificationDTO.class));

        TaskUrgeServiceImpl service = new TaskUrgeServiceImpl(
                taskService, notificationService, notificationRepository,
                instanceRepository, userRepository);

        service.urgeCurrentTask(100L);

        ArgumentCaptor<NotificationCreateRequest> captor =
                ArgumentCaptor.forClass(NotificationCreateRequest.class);
        verify(notificationService, org.mockito.Mockito.times(3)).createNotification(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationCreateRequest::getReceiverId)
                .containsExactly(20L, 21L, 22L);
    }

    private void authenticate(Long userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("applicant");
        user.setEnabled(1);
        user.setDeleted(0);
        CurrentUser currentUser = new CurrentUser(
                user, List.of(new SimpleGrantedAuthority("ROLE_NORMAL_USER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(currentUser, null, currentUser.getAuthorities()));
    }
}
