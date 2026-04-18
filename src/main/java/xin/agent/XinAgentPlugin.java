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
import xin.agent.commands.*;
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
    public xin.agent.trackers.ChatTracker chatTracker;
    public ExecutorService executorService;
    private ScheduledExecutorService scheduler;
    
    // 用于将任务系统与寻路系统融合
    public String currentMovementTaskId = null;

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

            chatTracker = new xin.agent.trackers.ChatTracker();
            Bot.Instance.getPluginManager().events().registerEvents(chatTracker, this);
            logger.info("ChatTracker initialized.");

            Bot.Instance.getPluginManager().events().registerEvents(new PrivateMessageListener(), this);
            logger.info("PrivateMessageListener initialized.");
            
            Bot.Instance.getPluginManager().events().registerEvents(new xin.agent.listeners.MovementTeleportListener(), this);
            logger.info("MovementTeleportListener initialized.");
            
            Bot.Instance.getPluginManager().registerCommand(new AgentCommand(), new AgentCommandExecutor(), this);
            Bot.Instance.getPluginManager().registerCommand(new AgentTaskCommand(), new AgentTaskCommandExecutor(), this);
            Bot.Instance.getPluginManager().registerCommand(new AgentClearCommand(), new AgentClearCommandExecutor(), this);
            logger.info("Agent commands registered.");

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
                if (agentManager.isProcessing()) return;

                List<Task> tasks = agentManager.getTaskManager().getTasks();
                boolean hasInProgress = tasks.stream().anyMatch(t -> t.getStatus() == Task.Status.IN_PROGRESS);

                if (hasInProgress) {
                    logger.info("[TaskLoop] 发现正在进行中的任务，唤醒 AI 继续工作...");
                    
                    String statusContext = "";
                    if (MovementSync.Instance != null) {
                        boolean isMoving = MovementSync.Instance.getMovementController().getCurrentMovement() != null;
                        if (isMoving) {
                            // 如果正在进行任何移动 (寻路、walkTo等)，直接跳过本次循环，绝对不打扰 AI，节省 Token
                            return;
                        }

                        org.joml.Vector3i goal = MovementSync.Instance.getActiveGoal();
                        if (goal != null) {
                            org.joml.Vector3d currentPos = MovementSync.Instance.position.get();
                            double dist = currentPos.distance(new org.joml.Vector3d(goal.x + 0.5, goal.y, goal.z + 0.5));
                            
                            if (dist < 2.0) {
                                // 到达目标
                                MovementSync.Instance.setActiveGoal(null);
                                MovementSync.Instance.getMovementController().cancelAll();
                                
                                // 自动融合任务系统：如果绑定了任务，则自动标记为完成
                                if (currentMovementTaskId != null && agentManager != null && agentManager.getTaskManager() != null) {
                                    agentManager.getTaskManager().updateTaskStatus(currentMovementTaskId, Task.Status.DONE);
                                    statusContext = String.format("\n[状态提示] 机器人已成功到达寻路目标 (%d, %d, %d)！系统已自动将对应的任务(ID: %s)标记为 DONE。请检查环境并执行下一步操作。", goal.x, goal.y, goal.z, currentMovementTaskId);
                                    currentMovementTaskId = null; // 清除
                                } else {
                                    statusContext = String.format("\n[状态提示] 机器人已成功到达寻路目标 (%d, %d, %d)！请执行下一步操作，如果该步骤的任务已完成，请记得 updateTaskStatus。", goal.x, goal.y, goal.z);
                                }
                            } else {
                                // 静止但未到达目标 (意味着卡死在路上了)
                                MovementSync.Instance.setActiveGoal(null);
                                MovementSync.Instance.getMovementController().cancelAll();
                                statusContext = String.format("\n[状态提示] 寻路已强制中断！你设定了前往 (%d, %d, %d) 的目标，但机器人目前被完全卡住静止 (距离目标还有 %.1f 格)。为防止死循环，系统已自动取消该次寻路。请务必检查周围环境(方块)，必要时挖掘障碍物、搭桥或换个坐标重新寻路。", goal.x, goal.y, goal.z, dist);
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
