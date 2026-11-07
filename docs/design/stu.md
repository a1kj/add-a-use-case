Student (STU) Design
Realizes: UC-STU-deactivate-student; FR-SEC-active-account.
Depends on: BR-student-lifecycle, BR-student-deactivation-membership, BR-deactivated-student-evaluations, BR-submission-owed.
Architecture: [Architectural design](architectural-design.md), especially foundation/performance-tracking components and security conventions.
Scope: deactivation only; UC-STU-reactivate-student is not implemented here.

Overview
Student deactivation is a dedicated instructor action, separate from editing contact information. The current system exposes an enabled field in EditStudentForm.vue and changes it through StudentService.updateStudent; this bypasses the warning/confirmation required by the use case. A dedicated endpoint and confirmation UI replace that path. The existing Student.enabled flag is the state of record. Existing section/team membership and previously submitted activity/evaluation records are retained.
Components and classes
Component	Status	Responsibility
frontend/src/pages/students/Students.vue	Modify	Present Deactivate action only for active students; refresh after success.
frontend/src/pages/students/EditStudentForm.vue	Modify	Remove editable Status so generic updates cannot deactivate/reactivate.
frontend/src/apis/student/index.ts and types.ts	Modify	Add deactivateStudent(studentId) API call and typed result.
backend/src/main/java/team/projectpulse/student/StudentController.java	Modify	Add POST /students/{studentId}/deactivate handler returning the existing Result envelope.
backend/src/main/java/team/projectpulse/student/StudentService.java	Modify	Validate active state and set enabled=false transactionally; stop generic updateStudent from changing enabled.
backend/src/main/java/team/projectpulse/student/StudentRepository.java / Student.java	Reuse	Find target and persist the existing enabled flag; retain team/section associations.
backend/src/main/java/team/projectpulse/student/StudentSecurityService.java	Modify	Check either assigned section instructor or course admin owning the student's course; fail closed.
backend/src/main/java/team/projectpulse/security/authorizationmanagers/	New	A deactivation-specific authorization manager extracting studentId and delegating to the section/course-permission check.
backend/src/main/java/team/projectpulse/security/SecurityConfiguration.java	Modify	Match and authorize the POST deactivation endpoint before fallback anyRequest().permitAll().
JWT bearer authentication chain / user-account lookup	Modify	Recheck current enabled state for each protected bearer request. A previously issued valid JWT must not preserve access after deactivation.
backend/src/main/java/team/projectpulse/evaluation/EvaluationService.java	Modify	Reject newly submitted evaluations targeting disabled students; retain existing evaluations and historical reporting.
backend/src/main/java/team/projectpulse/notification/SubmissionStatusService.java	Reuse	Continue excluding inactive students from owed-item/reminder computations.


Do not add a new deactivation table or timestamp. This use case applies eligibility at submission time: historical records are preserved, but new evaluations targeting inactive students are rejected even when their requested week precedes deactivation.
Sequence
UC-STU-deactivate-student: main success scenario
```mermaid
sequenceDiagram
    actor I as Course admin / assigned instructor
    participant UI as Students.vue
    participant API as Student API client
    participant SEC as SecurityConfiguration + DeactivationAuthorizationManager
    participant C as StudentController
    participant S as StudentService
    participant DB as StudentRepository
    I->>UI: Select student, choose Deactivate (steps 1-3)
    UI-->>I: Explain loss of access, stopped obligations, retained history; Confirm / Cancel (step 4)
    I->>UI: Confirm (step 5)
    UI->>API: deactivateStudent(studentId)
    API->>SEC: POST /students/{studentId}/deactivate
    SEC->>SEC: Verify caller is student's course admin or section instructor
    SEC->>C: Authorized request
    C->>S: deactivateStudent(studentId)
    S->>DB: findById(studentId)
    DB-->>S: Existing active Student
    S->>S: setEnabled(false); keep membership and records
    S->>DB: save(student)
    DB-->>S: Disabled student
    S-->>C: Success
    C-->>API: Result(success=true, ...)
    API-->>UI: Success
    UI-->>I: Deactivation confirmation; refresh student status (step 6)
```
For Cancel, no HTTP request is made. For unauthorized actors, the authorization manager denies the call before controller execution. For missing/already-inactive students, the service does not mutate records. An unexpected persistence failure rolls back the transaction; the UI shows failure, not success.
API contract
Method and route	Authorized caller	Request	Success	Failure
POST ${api.endpoint.base-url}/students/{studentId}/deactivate	Course admin of student's course OR instructor assigned to student's section	Path integer studentId, no JSON body	HTTP 200, existing Result success envelope	403 wrong actor; 404 missing student where request reaches service; 409 already inactive; 5xx unexpected persistence failure. Map exceptions with the project's existing error handling conventions.
PUT ${api.endpoint.base-url}/students/{studentId}	Existing edit authorization; unchanged for profile edits	Existing StudentDto; enabled field ignored by service	Profile updated only	Existing edit errors; no lifecycle change


The existing StudentInstructorAuthorizationManager only recognizes a section instructor; do not simply reuse it as the complete deactivation rule, because BR-student-lifecycle also allows the course admin. Permission evaluation must not leak another student's details. Server-side authorization is mandatory even if the frontend hides the action.
Security and data decisions
1. Dedicated action instead of editing Status. Rejected: retaining the Status dropdown as an alternative. Reason: it bypasses mandatory confirmation and obscures the lifecycle action. Remove/ignore the generic enabled update server-side, not just in Vue.
2. Keep section and team links. Rejected: removing the student from a team. Reason: avoid disrupting historical records and current associations. Inactive members are excluded from future owed peer-evaluation obligations under BR-submission-owed.
3. Protect every subsequent bearer request. Rejected: checking enabled only during login and relying on JWT expiration. Reason: JwtProvider issues two-hour JWTs, and FR-SEC-active-account requires denial even with otherwise-valid credentials. Integrate a current-account check into authentication for protected requests; cover missing and disabled accounts, and avoid accidentally blocking anonymous/public routes. The exact integration point must be validated against the existing Spring Security chain during implementation.
4. Preserve historical evaluation reports. Rejected: deleting historical entries or universally filtering inactive students out of every report. Reason: the past record must remain accessible. Newly submitted evaluations cannot target inactive students, regardless of requested week. Do not change the report's historical-data projection merely to suppress future obligations.
5. No new database migration. Rejected: a deactivation timestamp as part of this feature. Reason: requirements do not require retroactive eligibility by deactivation week. Existing Student.enabled is sufficient.
6. No reactivation implementation. Its Associated Information describes retained team membership; only deactivation is built in this PR.
Test list (design gate)
ID	Flow / oracle	Level	Assertions
STU-D01	Main success steps 1-6, BR-student-lifecycle	Integration	Authorized section instructor deactivates active student, response successful, enabled=false persisted.
STU-D02	Main success and section/course authorization	Integration	Owning course admin can deactivate an enrolled student even if not assigned as section instructor.
STU-D03	Extension 2a (student missing)	Integration	Nonexistent target never mutates data; documented non-success response.
STU-D04	Extension 3a (already inactive)	Service + integration	Second deactivation is rejected, with no save or false success message.
STU-D05	Extension 3b (wrong instructor / student caller)	Integration	Caller who is neither course admin nor assigned section instructor receives 403; account remains active.
STU-D06	Extension 4a (Cancel)	Frontend/component	Cancel or dialog close sends no POST; original status remains.
STU-D07	Extension 6a (failure)	Service + frontend	Persist error rolls back/no success; UI shows a failure message.
STU-D08	BR-student-deactivation-membership / historical preservation	Integration	Team/section membership, old weekly activities, and old peer evaluations remain unchanged.
STU-D09	BR-deactivated-student-evaluations	Service + integration	New peer evaluation targeting disabled student is rejected, even for an earlier week.
STU-D10	BR-submission-owed	Service	Inactive student receives no new owed items; active teammates do not owe evaluations of disabled member.
STU-D11	FR-SEC-active-account	Security integration	Existing valid bearer token becomes unusable for protected resources after deactivation; disabled student cannot log in.
STU-D12	Lifecycle bypass regression	Integration	Generic PUT /students/{studentId} cannot toggle enabled, even if submitted by an instructor.
STU-D13	Historical report behavior	Integration	Previously recorded scores remain visible to authorized instructor after deactivation.
STU-D14	UI confirmed flow	Frontend/component or manual	Warning clearly explains consequences; Confirm issues exactly one request and refreshes status.


For every test, the oracle is the identified use-case step, extension, FR, or BR, not the current implementation. If a frontend test runner is unavailable, record STU-D06/STU-D14 as untested (or manually checked, not automated); traceability Verify cannot claim complete automated coverage.
Trunk-first implementation and review
Implement shared/security changes in their own early commits, inspect those before feature-specific frontend changes. Suggested order: (1) close updateStudent status bypass and implement active-account bearer enforcement, (2) new authorization manager and security rule, (3) backend deactivation handler/service, (4) evaluation eligibility, (5) API/UI and tests. Keep test changes alongside the production slice they verify.

Review every test against its specification oracle. Temporarily mutate a deactivation production line (such as setEnabled(false) to setEnabled(true)), run a targeted test, record whether it turns red, restore the line, and rerun. Do not report tests as executed until they actually run. The build PR stays open for review.
