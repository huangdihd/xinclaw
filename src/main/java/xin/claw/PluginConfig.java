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

package xin.claw;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginConfig {
    private static final Logger logger = LoggerFactory.getLogger(PluginConfig.class);

    public static String apiKey = "demo";
    public static String apiBaseUrl = "";
    public static String modelName = "gpt-4o-mini";
    public static boolean enableThinking = false;
    /** 单次模型 API 请求超时（秒）。 */
    public static int apiTimeoutSeconds = 180;
    public static java.util.Set<String> privateMessageBlacklist = new java.util.HashSet<>();
    /** 聊天记忆保留的最大消息条数，<=0 表示不限制（不建议，会导致 token 无限增长） */
    public static int maxMemoryMessages = 150;
    /** 自主任务循环的检查间隔（秒） */
    public static int taskLoopIntervalSeconds = 15;
    /** 低于该血量(0-20)且仍在下降时主动唤醒 AI 避险 */
    public static float lowHealthThreshold = 10.0f;

    private static File getConfigFile() {
        return new File(getDataDir(), "config.properties");
    }

    /** 插件数据目录(…/XinClaw)。首次访问时若发现旧的 XinAgent 目录则自动迁移，保留配置、记忆与任务数据。 */
    public static synchronized File getDataDir() {
        String pluginDirStr = xin.bbtt.mcbot.Bot.INSTANCE.getConfig().getConfigData().getPlugin().getDirectory();
        File newDir = new File(pluginDirStr, "XinClaw");
        File oldDir = new File(pluginDirStr, "XinAgent");
        if (!newDir.exists() && oldDir.exists()) {
            if (oldDir.renameTo(newDir)) {
                logger.info("已将旧数据目录 XinAgent 迁移为 XinClaw。");
            } else {
                logger.warn("无法自动迁移旧数据目录 {} -> {}，请手动重命名。", oldDir, newDir);
            }
        }
        if (!newDir.exists() && !newDir.mkdirs()) {
            logger.error("无法创建数据目录 {}", newDir);
        }
        return newDir;
    }

    /** 更新单个配置项并写回配置文件，保留文件中已有的其他配置。 */
    public static synchronized boolean updateProperty(String key, String value) {
        File file = getConfigFile();
        Properties props = new Properties();
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                logger.error("Failed to read config file for update", e);
                return false;
            }
        }
        props.setProperty(key, value);
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "XinClaw Configuration");
            return true;
        } catch (IOException e) {
            logger.error("Failed to persist config update", e);
            return false;
        }
    }

    public static void loadConfig() {
        File file = getConfigFile();
        String configFileStr = file.getAbsolutePath();
        Properties props = new Properties();

        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                apiKey = props.getProperty("api_key", apiKey);
                apiBaseUrl = props.getProperty("api_base_url", apiBaseUrl);
                modelName = props.getProperty("model_name", modelName);
                enableThinking = Boolean.parseBoolean(props.getProperty("enable_thinking", String.valueOf(enableThinking)));

                try {
                    maxMemoryMessages = Integer.parseInt(props.getProperty("max_memory_messages", String.valueOf(maxMemoryMessages)).trim());
                    apiTimeoutSeconds = Integer.parseInt(props.getProperty("api_timeout_seconds", String.valueOf(apiTimeoutSeconds)).trim());
                    taskLoopIntervalSeconds = Integer.parseInt(props.getProperty("task_loop_interval_seconds", String.valueOf(taskLoopIntervalSeconds)).trim());
                    lowHealthThreshold = Float.parseFloat(props.getProperty("low_health_threshold", String.valueOf(lowHealthThreshold)).trim());
                } catch (NumberFormatException e) {
                    logger.warn("Invalid numeric config value, using defaults where parsing failed", e);
                }
                if (taskLoopIntervalSeconds < 5) {
                    logger.warn("task_loop_interval_seconds={} 过小，已强制设为 5 秒，防止 API 调用过于频繁", taskLoopIntervalSeconds);
                    taskLoopIntervalSeconds = 5;
                }
                if (apiTimeoutSeconds < 10) {
                    logger.warn("api_timeout_seconds={} 过小，已强制设为 10 秒", apiTimeoutSeconds);
                    apiTimeoutSeconds = 10;
                }

                String blacklistStr = props.getProperty("private_message_blacklist", "");
                privateMessageBlacklist.clear();
                if (!blacklistStr.isEmpty()) {
                    for (String s : blacklistStr.split(",")) {
                        privateMessageBlacklist.add(s.trim().toLowerCase());
                    }
                }
                
                logger.info("Configuration loaded from {}", configFileStr);
            } catch (IOException e) {
                logger.error("Failed to load config file", e);
            }
        } else {
            props.setProperty("api_key", apiKey);
            props.setProperty("api_base_url", apiBaseUrl);
            props.setProperty("model_name", modelName);
            props.setProperty("enable_thinking", String.valueOf(enableThinking));
            props.setProperty("api_timeout_seconds", String.valueOf(apiTimeoutSeconds));
            props.setProperty("private_message_blacklist", "back,help");
            props.setProperty("max_memory_messages", String.valueOf(maxMemoryMessages));
            props.setProperty("task_loop_interval_seconds", String.valueOf(taskLoopIntervalSeconds));
            props.setProperty("low_health_threshold", String.valueOf(lowHealthThreshold));
            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "XinClaw Configuration");
                logger.info("Default configuration created at {}", configFileStr);
            } catch (IOException e) {
                logger.error("Failed to create default config file", e);
            }
        }
    }
}
