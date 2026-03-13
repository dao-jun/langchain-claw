package org.monarch.langchain.claw.session;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.monarch.langchain.claw.common.SessionState;

public final class SessionStateMachine {

    private static final Map<SessionState, Set<SessionState>> ALLOWED_TRANSITIONS = createAllowedTransitions();

    private SessionStateMachine() {
    }

    public static void validate(String sessionId, SessionState from, SessionState to) {
        if (from == null || to == null || from == to) {
            return;
        }
        Set<SessionState> allowedTargets = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowedTargets.contains(to)) {
            throw new InvalidSessionStateTransitionException(sessionId, from, to);
        }
    }

    private static Map<SessionState, Set<SessionState>> createAllowedTransitions() {
        Map<SessionState, Set<SessionState>> transitions = new EnumMap<>(SessionState.class);
        transitions.put(SessionState.INIT, EnumSet.of(SessionState.PLAN_GENERATED, SessionState.FAILED));
        transitions.put(SessionState.PLAN_GENERATED, EnumSet.of(SessionState.PLAN_REVIEWED, SessionState.WAITING_FOR_USER, SessionState.EXECUTING, SessionState.FAILED));
        transitions.put(SessionState.PLAN_REVIEWED, EnumSet.of(SessionState.EXECUTING, SessionState.WAITING_FOR_USER, SessionState.FAILED));
        transitions.put(SessionState.WAITING_FOR_USER, EnumSet.of(SessionState.PLAN_GENERATED, SessionState.FAILED));
        transitions.put(SessionState.EXECUTING, EnumSet.of(SessionState.COMPLETED, SessionState.FAILED));
        transitions.put(SessionState.COMPLETED, EnumSet.of(SessionState.PLAN_GENERATED, SessionState.FAILED));
        transitions.put(SessionState.FAILED, EnumSet.of(SessionState.PLAN_GENERATED));
        return transitions;
    }
}
