/*
 *   Copyright (C) 2026 huangdihd
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xin.agent.utils;

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
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("items.json")),
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
        return entry != null ? (entry.displayName != null ? entry.displayName : entry.name) : "未知物品(ID:" + id + ")";
    }

    private static class ItemEntry {
        int id;
        String name;
        String displayName;
    }
}
