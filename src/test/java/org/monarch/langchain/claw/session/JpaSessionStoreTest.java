package org.monarch.langchain.claw.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.monarch.langchain.claw.common.SessionState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
class JpaSessionStoreTest {

    @Autowired
    private SessionStore sessionStore;

    @Test
    void shouldIncrementVersionAcrossValidStateTransitions() {
        UserSession session = sessionStore.getOrCreate("session-user-1", "session-consistency-1");
        Long initialVersion = session.getVersion();

        sessionStore.updateState("session-user-1", "session-consistency-1", SessionState.PLAN_GENERATED, null, "{}");
        UserSession afterPlan = sessionStore.find("session-user-1", "session-consistency-1").orElseThrow();
        sessionStore.updateState("session-user-1", "session-consistency-1", SessionState.PLAN_REVIEWED, null, "{}");
        UserSession afterReview = sessionStore.find("session-user-1", "session-consistency-1").orElseThrow();
        sessionStore.updateState("session-user-1", "session-consistency-1", SessionState.EXECUTING, null, "{}");
        sessionStore.updateState("session-user-1", "session-consistency-1", SessionState.COMPLETED, null, "{}");
        UserSession completed = sessionStore.find("session-user-1", "session-consistency-1").orElseThrow();

        assertThat(initialVersion).isNotNull();
        assertThat(afterPlan.getVersion()).isGreaterThan(initialVersion);
        assertThat(afterReview.getVersion()).isGreaterThan(afterPlan.getVersion());
        assertThat(completed.getState()).isEqualTo(SessionState.COMPLETED);
    }

    @Test
    void shouldRejectInvalidStateTransition() {
        sessionStore.getOrCreate("session-user-2", "session-consistency-2");

        assertThatThrownBy(() ->
            sessionStore.updateState("session-user-2", "session-consistency-2", SessionState.EXECUTING, null, "{}"))
            .isInstanceOf(InvalidSessionStateTransitionException.class)
            .hasMessageContaining("INIT -> EXECUTING");
    }

    @Test
    void shouldDetectOptimisticLockConflictForStaleSessionSave() {
        sessionStore.getOrCreate("session-user-3", "session-consistency-3");
        UserSession first = sessionStore.find("session-user-3", "session-consistency-3").orElseThrow();
        UserSession second = sessionStore.find("session-user-3", "session-consistency-3").orElseThrow();

        first.setState(SessionState.PLAN_GENERATED);
        first.setCurrentPlanJson("{\"step\":1}");
        UserSession saved = sessionStore.save(first);

        second.setState(SessionState.FAILED);
        second.setCurrentPlanJson("{\"step\":2}");

        assertThat(saved.getVersion()).isGreaterThan(first.getVersion());
        assertThatThrownBy(() -> sessionStore.save(second))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
