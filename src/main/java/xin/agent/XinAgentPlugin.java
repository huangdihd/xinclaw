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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xin.bbtt.mcbot.Bot;
import xin.bbtt.mcbot.plugin.Plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class XinAgentPlugin implements Plugin {

    private static final Logger logger = LoggerFactory.getLogger(XinAgentPlugin.class);
    public static XinAgentPlugin Instance;
    public AgentManager agentManager;
    public InventoryTracker inventoryTracker;
    public DimensionTracker dimensionTracker;
    public ExecutorService executorService;

    public XinAgentPlugin() {
    }

    @Override
    public void onLoad() {
        logger.info("Loading XinAgentPlugin...");
    }

    @Override
    public void onUnload() {
        logger.info("Unloading XinAgentPlugin...");
    }

    @Override
    public void onEnable() {
        logger.info("Enabling XinAgentPlugin with Langchain4j...");
        Instance = this;
        this.executorService = Executors.newCachedThreadPool();
        
        try {
            PluginConfig.loadConfig();
            
            agentManager = new AgentManager();
            logger.info("AgentManager initialized.");

            inventoryTracker = new InventoryTracker();
            Bot.Instance.getPluginManager().events().registerEvents(inventoryTracker, this);
            logger.info("InventoryTracker initialized.");

            dimensionTracker = new DimensionTracker();
            Bot.Instance.getPluginManager().events().registerEvents(dimensionTracker, this);
            logger.info("DimensionTracker initialized.");

            Bot.Instance.getPluginManager().events().registerEvents(new PrivateMessageListener(), this);
            logger.info("PrivateMessageListener initialized.");
            
            Bot.Instance.getPluginManager().registerCommand(new AgentCommand(), new AgentCommandExecutor(), this);
            logger.info("Agent command registered.");
        } catch (Throwable e) {
            logger.error("Failed to initialize XinAgentPlugin", e);
        }
    }

    @Override
    public void onDisable() {
        logger.info("Disabling XinAgentPlugin.");
        if (executorService == null) {
            return;
        }
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    @Override
    public String getName() {
        return "XinAgent";
    }

    @Override
    public String getVersion() {
        return "1.0-SNAPSHOT";
    }

    @Override
    public Logger getLogger() {
        return logger;
    }
}
