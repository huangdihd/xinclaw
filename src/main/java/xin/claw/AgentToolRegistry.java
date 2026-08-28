package xin.claw;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AgentToolRegistry {
    private final List<Object> defaultTools;
    private final List<Object> externalTools = new ArrayList<>();

    public AgentToolRegistry(List<Object> defaultTools) {
        Objects.requireNonNull(defaultTools, "defaultTools");
        if (defaultTools.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("default tools must not contain null");
        }
        this.defaultTools = List.copyOf(defaultTools);
    }

    public synchronized boolean registerExternal(Object tool) {
        Objects.requireNonNull(tool, "tool");
        if (containsIdentity(externalTools, tool)) return false;
        externalTools.add(tool);
        return true;
    }

    public synchronized boolean registerExternal(Object tool, Runnable rebuild) {
        Objects.requireNonNull(rebuild, "rebuild");
        if (!registerExternal(tool)) return false;
        try {
            rebuild.run();
            return true;
        } catch (RuntimeException error) {
            unregisterExternal(tool);
            throw error;
        }
    }

    public synchronized boolean unregisterExternal(Object tool) {
        Objects.requireNonNull(tool, "tool");
        for (int index = 0; index < externalTools.size(); index++) {
            if (externalTools.get(index) == tool) {
                externalTools.remove(index);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean unregisterExternal(Object tool, Runnable rebuild) {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(rebuild, "rebuild");
        int removedIndex = -1;
        for (int index = 0; index < externalTools.size(); index++) {
            if (externalTools.get(index) == tool) {
                removedIndex = index;
                break;
            }
        }
        if (removedIndex < 0) return false;
        externalTools.remove(removedIndex);
        try {
            rebuild.run();
            return true;
        } catch (RuntimeException error) {
            externalTools.add(removedIndex, tool);
            throw error;
        }
    }

    public synchronized List<Object> snapshot() {
        List<Object> tools = new ArrayList<>(defaultTools.size() + externalTools.size());
        tools.addAll(defaultTools);
        tools.addAll(externalTools);
        return List.copyOf(tools);
    }

    private static boolean containsIdentity(List<Object> tools, Object candidate) {
        for (Object tool : tools) {
            if (tool == candidate) return true;
        }
        return false;
    }
}
