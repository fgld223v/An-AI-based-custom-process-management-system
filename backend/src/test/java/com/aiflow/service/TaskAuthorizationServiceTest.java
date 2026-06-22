package com.aiflow.service;

import com.aiflow.entity.UserEntity;
import com.aiflow.security.CurrentUser;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAuthorizationServiceTest {

    @Mock
    private TaskService taskService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedUserCanOperateTask() {
        authenticate(20L, "normal_user");
        Task task = task("task-1", "20");

        assertThat(new TaskAuthorizationService(taskService).requireOperableTask(task)).isSameAs(task);
    }

    @Test
    void superAdministratorCannotOperateAnotherUsersTask() {
        authenticate(1L, "super_admin");
        Task task = task("task-1", "20");

        assertThatThrownBy(() -> new TaskAuthorizationService(taskService).requireOperableTask(task))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void candidateIsClaimedBeforeOperatingTask() {
        authenticate(20L, "normal_user");
        Task unassigned = task("task-1", null);
        Task claimed = task("task-1", "20");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.taskCandidateUser("20")).thenReturn(query);
        when(query.count()).thenReturn(1L);
        when(query.singleResult()).thenReturn(claimed);

        Task result = new TaskAuthorizationService(taskService).requireOperableTask(unassigned);

        assertThat(result).isSameAs(claimed);
        verify(taskService).claim("task-1", "20");
    }

    @Test
    void historicTaskIsVisibleOnlyToItsAssignee() {
        authenticate(20L, "normal_user");
        HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
        when(historicTask.getAssignee()).thenReturn("21");

        assertThatThrownBy(() -> new TaskAuthorizationService(taskService).assertCanView(historicTask))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Task task(String id, String assignee) {
        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn(id);
        when(task.getAssignee()).thenReturn(assignee);
        return task;
    }

    private void authenticate(Long id, String systemRole) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setUsername("user-" + id);
        entity.setPassword("-");
        entity.setSystemRole(systemRole);
        entity.setRole("USER");
        entity.setEnabled(1);
        CurrentUser user = new CurrentUser(entity,
                List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.toUpperCase())));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
