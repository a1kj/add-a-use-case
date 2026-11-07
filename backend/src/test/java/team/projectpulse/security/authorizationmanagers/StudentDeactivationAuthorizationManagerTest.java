
package team.projectpulse.security.authorizationmanagers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentDeactivationAuthorizationManagerTest {

    @Mock
    StudentSecurityService studentSecurityService;

    @InjectMocks
    StudentDeactivationAuthorizationManager manager;

    private RequestAuthorizationContext context(
            Map<String, String> variables) {
        return new RequestAuthorizationContext(
                mock(HttpServletRequest.class), variables);
    }

    @Test
    void allowsAuthorizedInstructor() {
        given(studentSecurityService
                .canCurrentUserDeactivateStudent(7))
                .willReturn(true);

        AuthorizationResult result = manager.authorize(
                () -> null,
                context(Map.of("studentId", "7")));

        assertThat(result.isGranted()).isTrue();
    }

    @Test
    void deniesUnauthorizedInstructor() {
        given(studentSecurityService
                .canCurrentUserDeactivateStudent(7))
                .willReturn(false);

        AuthorizationResult result = manager.authorize(
                () -> null,
                context(Map.of("studentId", "7")));

        assertThat(result.isGranted()).isFalse();
    }

    @Test
    void deniesMissingStudentId() {
        AuthorizationResult result = manager.authorize(
                () -> null,
                context(Map.of()));

        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(studentSecurityService);
    }

    @Test
    void deniesInvalidStudentId() {
        AuthorizationResult result = manager.authorize(
                () -> null,
                context(Map.of("studentId", "abc")));

        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(studentSecurityService);
    }
}
