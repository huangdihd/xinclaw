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
import xin.bbtt.MovementSync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import xin.agent.commands.AgentCommand;
import xin.agent.commands.AgentCommandExecutor;
import xin.agent.listeners.PrivateMessageListener;
import xin.agent.trackers.DimensionTracker;
import xin.agent.trackers.InventoryTracker;
import xin.agent.tasks.Task;

public class XinAgentPlugin implements Plugin {

    private static final Logger logger = LoggerFactory.getLogger(XinAgentPlugin.class);
    public static XinAgentPlugin Instance;
    public AgentManager agentManager;
    public InventoryTracker inventoryTracker;
    public DimensionTracker dimensionTracker;
    public ExecutorService executorService;
    private ScheduledExecutorService scheduler;

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
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
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
            
            Bot.Instance.getPluginManager().events().registerEvents(new xin.agent.listeners.MovementTeleportListener(), this);
            logger.info("MovementTeleportListener initialized.");
            
            Bot.Instance.getPluginManager().registerCommand(new AgentCommand(), new AgentCommandExecutor(), this);
            logger.info("Agent command registered.");

            // 启动自主任务循环：每 15 秒检查一次
            startTaskLoop();
        } catch (Throwable e) {
            logger.error("Failed to initialize XinAgentPlugin", e);
        }
    }

    private void startTaskLoop() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (agentManager == null || agentManager.getTaskManager() == null) return;
                if (!Bot.Instance.isRunning()) return;

                List<Task> tasks = agentManager.getTaskManager().getTasks();
                boolean hasInProgress = tasks.stream().anyMatch(t -> t.getStatus() == Task.Status.IN_PROGRESS);

                if (hasInProgress) {
                    logger.info("[TaskLoop] 发现正在进行中的任务，唤醒 AI 继续工作...");
                    
                    String statusContext = "";
                    if (MovementSync.Instance != null) {
                        org.joml.Vector3i goal = MovementSync.Instance.getActiveGoal();
                        boolean isMoving = MovementSync.Instance.getMovementController().getCurrentMovement() != null;
                        
                        if (goal != null) {
                            if (!isMoving) {
                                statusContext = String.format("\n[状态提示] 你当前设定了寻路目标 (%d, %d, %d)，但机器人目前处于静止状态。这通常意味着路径被完全堵死或目标不可达，请尝试重新规划路径或先挖掘周围障碍物。", goal.x, goal.y, goal.z);
                            } else {
                                statusContext = String.format("\n[状态提示] 机器人正在寻路前往 (%d, %d, %d)。", goal.x, goal.y, goal.z);
                            }
                        }
                    }

                    // 发送背景提示，让 AI 决定下一步动作
                    String response = agentManager.processMessage("[SYSTEM_TICK] 你当前有正在进行的任务。请检查环境并执行必要的工具调用以继续完成任务。如果你已经完成，请更新任务状态。" + statusContext);
                    if (response != null && !response.trim().isEmpty()) {
                        logger.info("[TaskLoop] AI 思考结果: {}", response);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in task loop", e);
            }
        }, 30, 15, TimeUnit.SECONDS); // 启动后延迟 30 秒开始，每 15 秒运行一次
    }

    @Override
    public void onDisable() {
        logger.info("Disabling XinAgentPlugin.");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
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
