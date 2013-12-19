package org.datafx.controller.validation.event;

import java.util.Set;

import javafx.event.Event;
import javafx.event.EventType;

import javax.validation.ConstraintViolation;

import org.datafx.controller.flow.event.AfterFlowActionEvent;

public class ValidationFinishedEvent extends Event {

	private static final long serialVersionUID = 1L;

	public static final EventType<AfterFlowActionEvent> DEFAULT_EVENT_TYPE =
            new EventType<>("ValidationViolationEvent");

    @SuppressWarnings("rawtypes")
	private Set<ConstraintViolation> violations;

    @SuppressWarnings("rawtypes")
	public ValidationFinishedEvent(Set<ConstraintViolation> violations) {
        super(DEFAULT_EVENT_TYPE);
        this.violations = violations;
    }

    @SuppressWarnings("rawtypes")
	public Set<ConstraintViolation> getViolations() {
		return violations;
	}
}