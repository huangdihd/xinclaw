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

package xin.claw.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.claw.PluginConfig;
import xin.claw.XinClawPlugin;
import xin.bbtt.mcbot.command.Command;
import xin.bbtt.mcbot.command.CommandExecutor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AgentModelCommandExecutor extends CommandExecutor {

    private static final Logger logger = LoggerFactory.getLogger(AgentModelCommandExecutor.class);
    private static final Pattern MODEL_ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    private static volatile List<String> cachedModels = Collections.emptyList();
    private static volatile long cacheTime = 0;
    private static final AtomicBoolean refreshing = new AtomicBoolean(false);

    @Override
    public void onCommand(Command command, String label, String[] args) {
        if (XinClawPlugin.INSTANCE == null || XinClawPlugin.INSTANCE.agentManager == null) {
            logger.error("AgentManager 未成功初始化！");
            return;
        }

        if (args.length == 0) {
            logger.info("当前模型: {} (API: {})", PluginConfig.modelName,
                    PluginConfig.apiBaseUrl.isEmpty() ? "OpenAI 官方" : PluginConfig.apiBaseUrl);
            logger.info("用法: {}", command.usage());
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            logger.info("正在从 API 获取模型列表...");
            runAsync(() -> {
                List<String> models = fetchModels();
                if (models.isEmpty()) {
                    logger.info("未获取到任何模型，请检查 api_base_url 和 api_key 配置。");
                } else {
                    logger.info("API 提供的模型 (共{}个): {}", models.size(), String.join(", ", models));
                }
            });
            return;
        }

        String newModel = args[0];
        String oldModel = PluginConfig.modelName;
        if (newModel.equals(oldModel)) {
            logger.info("模型已经是 {}，无需切换。", newModel);
            return;
        }

        // 切换前先打断进行中的思考，避免重建 agent 时有请求在途
        if (XinClawPlugin.INSTANCE.agentManager.isProcessing()) {
            XinClawPlugin.INSTANCE.agentManager.interruptProcessing();
            logger.info("已打断 AI 当前的思考以切换模型。");
        }

        PluginConfig.modelName = newModel;
        try {
            XinClawPlugin.INSTANCE.agentManager.initAgent();
        } catch (Exception e) {
            PluginConfig.modelName = oldModel;
            logger.error("切换模型失败，已回退到 {}", oldModel, e);
            return;
        }

        if (PluginConfig.updateProperty("model_name", newModel)) {
            logger.info("模型已从 {} 切换为 {} 并写入配置文件。", oldModel, newModel);
        } else {
            logger.warn("模型已切换为 {}，但写入配置文件失败，重启后会恢复为 {}。", newModel, oldModel);
        }
    }

    @Override
    public List<String> onTabComplete(Command cmd, String label, String[] args) {
        if (args.length != 1) return Collections.emptyList();

        // 缓存过期时异步刷新，本次先用现有缓存，避免阻塞补全
        if (System.currentTimeMillis() - cacheTime > CACHE_TTL_MS) {
            triggerAsyncRefresh();
        }

        String prefix = args[0].toLowerCase();
        List<String> suggestions = new ArrayList<>();
        if ("list".startsWith(prefix)) suggestions.add("list");
        suggestions.addAll(cachedModels.stream()
                .filter(m -> m.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList()));
        return suggestions;
    }

    private void triggerAsyncRefresh() {
        if (!refreshing.compareAndSet(false, true)) return;
        runAsync(() -> {
            try {
                List<String> models = fetchModels();
                if (!models.isEmpty()) {
                    cachedModels = models;
                }
                cacheTime = System.currentTimeMillis(); // 失败也记录时间，避免每次补全都重试
            } finally {
                refreshing.set(false);
            }
        });
    }

    private void runAsync(Runnable task) {
        if (XinClawPlugin.INSTANCE != null && XinClawPlugin.INSTANCE.executorService != null
                && !XinClawPlugin.INSTANCE.executorService.isShutdown()) {
            XinClawPlugin.INSTANCE.executorService.submit(task);
        } else {
            new Thread(task, "agentmodel-fetch").start();
        }
    }

    /** 通过 OpenAI 协议的 GET /models 接口获取模型 ID 列表。 */
    private static List<String> fetchModels() {
        String base = PluginConfig.apiBaseUrl == null || PluginConfig.apiBaseUrl.trim().isEmpty()
                ? "https://api.openai.com/v1"
                : PluginConfig.apiBaseUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String url = base + "/models";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + PluginConfig.apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("获取模型列表失败: HTTP {} from {}", response.statusCode(), url);
                return Collections.emptyList();
            }

            List<String> models = new ArrayList<>();
            Matcher matcher = MODEL_ID_PATTERN.matcher(response.body());
            while (matcher.find()) {
                models.add(matcher.group(1));
            }
            return models.stream().distinct().sorted().collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("获取模型列表失败: {}", e.toString());
            return Collections.emptyList();
        }
    }
}
