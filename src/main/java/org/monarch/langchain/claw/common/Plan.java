package org.monarch.langchain.claw.common;

import java.util.ArrayList;
import java.util.List;

public class Plan {

    private String rationale;
    private List<PlanStep> steps = new ArrayList<>();

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public List<PlanStep> getSteps() {
        return steps;
    }

    public void setSteps(List<PlanStep> steps) {
        this.steps = steps;
    }
}
