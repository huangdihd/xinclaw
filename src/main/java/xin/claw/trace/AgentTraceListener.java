package xin.claw.trace;

@FunctionalInterface
public interface AgentTraceListener {
    void onTraceEvent(AgentTraceEvent event);
}
