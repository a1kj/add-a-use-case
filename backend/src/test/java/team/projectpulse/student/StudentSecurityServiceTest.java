
package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentSecurityServiceTest {

    @Mock StudentRepository studentRepository;
    @Mock UserUtils userUtils;

    @InjectMocks StudentSecurityService service;

    @Mock Student student;
    @Mock Section section;
    @Mock Course course;
    @Mock Instructor assignedInstructor;
    @Mock Instructor courseAdmin;

    @BeforeEach
    void setup() {
        // Individual tests arrange their own data.
    }

    @Test
    void allowsAssignedInstructor() {
        given(studentRepository.findById(7))
                .willReturn(Optional.of(student));
        given(student.getSection()).willReturn(section);
        given(userUtils.getUserId()).willReturn(10);
        given(userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(section.getInstructors()).willReturn(Set.of(assignedInstructor));
        given(assignedInstructor.getId()).willReturn(10);

        assertThat(service.canCurrentUserDeactivateStudent(7)).isTrue();
    }

    @Test
    void allowsCourseAdmin() {
        given(studentRepository.findById(7))
                .willReturn(Optional.of(student));
        given(student.getSection()).willReturn(section);
        given(userUtils.getUserId()).willReturn(20);
        given(userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(section.getInstructors()).willReturn(Set.of());
        given(section.getCourse()).willReturn(course);
        given(course.getCourseAdmin()).willReturn(courseAdmin);
        given(courseAdmin.getId()).willReturn(20);

        assertThat(service.canCurrentUserDeactivateStudent(7)).isTrue();
    }

    @Test
    void deniesUnrelatedInstructor() {
        given(studentRepository.findById(7))
                .willReturn(Optional.of(student));
        given(student.getSection()).willReturn(section);
        given(userUtils.getUserId()).willReturn(99);
        given(userUtils.hasRole("ROLE_instructor")).willReturn(true);
        given(section.getInstructors()).willReturn(Set.of(assignedInstructor));
        given(assignedInstructor.getId()).willReturn(10);
        given(section.getCourse()).willReturn(course);
        given(course.getCourseAdmin()).willReturn(courseAdmin);
        given(courseAdmin.getId()).willReturn(20);

        assertThat(service.canCurrentUserDeactivateStudent(7)).isFalse();
    }

    @Test
    void deniesStudentAccount() {
        given(studentRepository.findById(7))
                .willReturn(Optional.of(student));
        given(student.getSection()).willReturn(section);
        given(userUtils.getUserId()).willReturn(7);
        given(userUtils.hasRole("ROLE_instructor")).willReturn(false);

        assertThat(service.canCurrentUserDeactivateStudent(7)).isFalse();
    }

    @Test
    void deniesMissingStudent() {
        given(studentRepository.findById(999))
                .willReturn(Optional.empty());

        assertThat(service.canCurrentUserDeactivateStudent(999)).isFalse();
        verifyNoInteractions(userUtils);
    }
}
