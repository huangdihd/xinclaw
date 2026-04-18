package xin.agent.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.Bot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TaskManager {
    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final List<Task> tasks = new CopyOnWriteArrayList<>();
    private final File storageFile;

    public TaskManager() {
        String pluginDir = Bot.Instance.getConfig().getConfigData().getPlugin().getDirectory();
        File configDir = new File(pluginDir, "XinAgent");
        if (!configDir.exists()) configDir.mkdirs();
        this.storageFile = new File(configDir, "tasks.json");
        load();
    }

    public void addTask(String desc) {
        tasks.add(new Task(desc));
        save();
    }

    public List<Task> getTasks() {
        return new ArrayList<>(tasks);
    }

    public boolean removeTask(String id) {
        boolean removed = tasks.removeIf(t -> t.getId().equalsIgnoreCase(id));
        if (removed) save();
        return removed;
    }

    public void clearTasks() {
        tasks.clear();
        save();
    }

    public boolean updateTaskStatus(String id, Task.Status status) {
        for (Task t : tasks) {
            if (t.getId().equalsIgnoreCase(id)) {
                t.setStatus(status);
                save();
                return true;
            }
        }
        return false;
    }

    private void save() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(storageFile), StandardCharsets.UTF_8)) {
            gson.toJson(tasks, writer);
        } catch (IOException e) {
            logger.error("Failed to save tasks", e);
        }
    }

    private void load() {
        if (!storageFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(storageFile), StandardCharsets.UTF_8)) {
            List<Task> loaded = gson.fromJson(reader, new TypeToken<List<Task>>(){}.getType());
            if (loaded != null) {
                tasks.clear();
                tasks.addAll(loaded);
            }
        } catch (IOException e) {
            logger.error("Failed to load tasks", e);
        }
    }
}
