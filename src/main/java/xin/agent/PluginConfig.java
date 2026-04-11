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

package xin.agent;

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

    public static void loadConfig() {
        String pluginDirStr = xin.bbtt.mcbot.Bot.Instance.getConfig().getConfigData().getPlugin().getDirectory();
        String configDirStr = pluginDirStr + File.separator + "XinAgent";
        String configFileStr = configDirStr + File.separator + "config.properties";

        File dir = new File(configDirStr);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(configFileStr);
        Properties props = new Properties();

        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                apiKey = props.getProperty("api_key", apiKey);
                apiBaseUrl = props.getProperty("api_base_url", apiBaseUrl);
                modelName = props.getProperty("model_name", modelName);
                enableThinking = Boolean.parseBoolean(props.getProperty("enable_thinking", String.valueOf(enableThinking)));
                logger.info("Configuration loaded from {}", configFileStr);
            } catch (IOException e) {
                logger.error("Failed to load config file", e);
            }
        } else {
            props.setProperty("api_key", apiKey);
            props.setProperty("api_base_url", apiBaseUrl);
            props.setProperty("model_name", modelName);
            props.setProperty("enable_thinking", String.valueOf(enableThinking));
            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "XinAgent Configuration");
                logger.info("Default configuration created at {}", configFileStr);
            } catch (IOException e) {
                logger.error("Failed to create default config file", e);
            }
        }
    }
}
