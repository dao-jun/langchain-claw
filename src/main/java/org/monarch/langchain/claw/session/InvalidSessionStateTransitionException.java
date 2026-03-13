package org.monarch.langchain.claw.session;

import org.monarch.langchain.claw.common.SessionState;

public class InvalidSessionStateTransitionException extends IllegalStateException {

    public InvalidSessionStateTransitionException(String sessionId, SessionState from, SessionState to) {
        super("Invalid session state transition for session " + sessionId + ": " + from + " -> " + to);
    }
}
