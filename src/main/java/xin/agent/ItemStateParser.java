package xin.agent;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class ItemStateParser {
    private final Map<Integer, ItemEntry> itemMap = new HashMap<>();
    public static ItemStateParser Instance = new ItemStateParser();

    private ItemStateParser() {
        loadItems();
    }

    private void loadItems() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("blocks.json")),
                StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            Gson gson = new Gson();
            List<ItemEntry> entries = gson.fromJson(content, new TypeToken<List<ItemEntry>>(){}.getType());
            for (ItemEntry entry : entries) {
                itemMap.put(entry.id, entry);
            }
        } catch (Exception e) {
            // Silently fail or log
        }
    }

    public String getItemName(int id) {
        ItemEntry entry = itemMap.get(id);
        return entry != null ? entry.name : "unknown_item_" + id;
    }

    private static class ItemEntry {
        int id;
        String name;
    }
}
