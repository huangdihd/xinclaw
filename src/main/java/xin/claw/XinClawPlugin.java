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
import xin.claw.listeners.PrivateMessageListener;
import xin.claw.trackers.DimensionTracker;
import xin.claw.trackers.InventoryTracker;
import xin.claw.tasks.Task;

public class XinClawPlugin implements Plugin {

    private static final Logger logger = LoggerFactory.getLogger(XinClawPlugin.class);
    public static XinClawPlugin INSTANCE;
    public AgentManager agentManager;
    public InventoryTracker inventoryTracker;
    public DimensionTracker dimensionTracker;
    public xin.claw.trackers.ChatTracker chatTracker;
    public xin.claw.trackers.HealthTracker healthTracker;
    public ExecutorService executorService;
    private ScheduledExecutorService scheduler;
    private volatile boolean teleportAgentNotificationsSuppressed;
    
    // 用于将任务系统与寻路系统融合
    public String currentMovementTaskId = null;

    public XinClawPlugin() {
    }

    public boolean isTeleportAgentNotificationsSuppressed() {
        return teleportAgentNotificationsSuppressed;
    }

    public void setTeleportAgentNotificationsSuppressed(boolean suppressed) {
        teleportAgentNotificationsSuppressed = suppressed;
    }

    @Override
    public void onLoad() {
        logger.info("Loading XinClawPlugin...");
    }

    @Override
    public void onUnload() {
        logger.info("Unloading XinClawPlugin...");
    }

    @Override
    public void onEnable() {
        logger.info("Enabling XinClawPlugin with Langchain4j...");
        INSTANCE = this;
        this.executorService = Executors.newCachedThreadPool();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        
        try {
            PluginConfig.loadConfig();
            
            agentManager = new AgentManager();
            logger.info("AgentManager initialized.");

            inventoryTracker = new InventoryTracker();
            Bot.INSTANCE.getPluginManager().events().registerEvents(inventoryTracker, this);
            logger.info("InventoryTracker initialized.");

            dimensionTracker = new DimensionTracker();
            Bot.INSTANCE.getPluginManager().events().registerEvents(dimensionTracker, this);
            logger.info("DimensionTracker initialized.");

            chatTracker = new xin.claw.trackers.ChatTracker();
            Bot.INSTANCE.getPluginManager().events().registerEvents(chatTracker, this);
            logger.info("ChatTracker initialized.");

            healthTracker = new xin.claw.trackers.HealthTracker();
            Bot.INSTANCE.getPluginManager().events().registerEvents(healthTracker, this);
            logger.info("HealthTracker initialized.");

            Bot.INSTANCE.getPluginManager().events().registerEvents(new PrivateMessageListener(), this);
            logger.info("PrivateMessageListener initialized.");
            
            Bot.INSTANCE.getPluginManager().events().registerEvents(new xin.claw.listeners.MovementTeleportListener(), this);
            logger.info("MovementTeleportListener initialized.");

            Bot.INSTANCE.getPluginManager().events().registerEvents(new xin.claw.listeners.DeathListener(), this);
            logger.info("DeathListener initialized.");

            // 启动自主任务循环（间隔可通过 task_loop_interval_seconds 配置）
            startTaskLoop();
        } catch (Throwable e) {
            logger.error("Failed to initialize XinClawPlugin", e);
        }
    }

    private void startTaskLoop() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (agentManager == null || agentManager.getTaskManager() == null) return;
                if (!Bot.INSTANCE.isRunning()) return;
                if (agentManager.isProcessing()) return;

                List<Task> tasks = agentManager.getTaskManager().getTasks();
                boolean hasInProgress = tasks.stream().anyMatch(t -> t.getStatus() == Task.Status.IN_PROGRESS);

                if (hasInProgress) {
                    logger.info("[TaskLoop] 发现正在进行中的任务，唤醒 AI 继续工作...");
                    
                    String statusContext = "";
                    if (MovementSync.INSTANCE != null) {
                        boolean isMoving = MovementSync.INSTANCE.getMovementController().getCurrentMovement() != null;
                        org.joml.Vector3i goal = MovementSync.INSTANCE.getActiveGoal();
                        
                        if (goal != null) {
                            org.joml.Vector3d currentPos = MovementSync.INSTANCE.position.get();
                            double dist = currentPos.distance(new org.joml.Vector3d(goal.x + 0.5, goal.y, goal.z + 0.5));
                            
                            if (dist < 2.0) {
                                // 到达目标
                                MovementSync.INSTANCE.setActiveGoal(null);
                                MovementSync.INSTANCE.getMovementController().cancelAll();
                                
                                // 自动融合任务系统：如果绑定了任务，则自动标记为完成
                                if (currentMovementTaskId != null && agentManager != null && agentManager.getTaskManager() != null) {
                                    agentManager.getTaskManager().updateTaskStatus(currentMovementTaskId, Task.Status.DONE);
                                    statusContext = String.format("\n[状态提示] 机器人已成功到达寻路目标 (%d, %d, %d)！系统已自动将对应的任务(ID: %s)标记为 DONE。请检查环境并执行下一步操作。", goal.x, goal.y, goal.z, currentMovementTaskId);
                                    currentMovementTaskId = null; // 清除
                                } else {
                                    statusContext = String.format("\n[状态提示] 机器人已成功到达寻路目标 (%d, %d, %d)！请执行下一步操作，如果该步骤的任务已完成，请记得 updateTaskStatus。", goal.x, goal.y, goal.z);
                                }
                            } else if (!isMoving) {
                                // 静止但未到达目标 (意味着卡死在路上了)
                                MovementSync.INSTANCE.setActiveGoal(null);
                                MovementSync.INSTANCE.getMovementController().cancelAll();
                                statusContext = String.format("\n[状态提示] 寻路已强制中断！你设定了前往 (%d, %d, %d) 的目标，但机器人目前被完全卡住静止 (距离目标还有 %.1f 格)。为防止死循环，系统已自动取消该次寻路。请务必检查周围环境(方块)，必要时挖掘障碍物、搭桥或换个坐标重新寻路。", goal.x, goal.y, goal.z, dist);
                            } else {
                                statusContext = String.format("\n[状态提示] 机器人目前正在向寻路目标 (%d, %d, %d) 移动中，当前距离目标还有 %.1f 格。你可以通过 stopWalking 随时打断寻路，或查阅其它工具。如果不打算打断移动，请仅简短回复正在路上即可。", goal.x, goal.y, goal.z, dist);
                            }
                        } else if (isMoving) {
                            statusContext = "\n[状态提示] 机器人目前正在进行物理移动，你如果想中止可以调用 stopWalking。";
                        }
                    }

                    if (healthTracker != null) {
                        statusContext += "\n[体征] " + healthTracker.getVitalsSummary();
                    }

                    // 直接附上未完成任务清单(含ID)，免去 AI 再调 listTasks，也避免它因不记得 ID 而跳过改状态
                    String pendingTasks = tasks.stream()
                            .filter(t -> t.getStatus() != Task.Status.DONE)
                            .map(Task::toString)
                            .collect(java.util.stream.Collectors.joining("\n"));

                    // 发送背景提示，让 AI 决定下一步动作
                    String response = agentManager.processMessage(
                            "[SYSTEM_TICK] 你有正在进行中的任务。当前未完成任务清单:\n" + pendingTasks
                            + "\n请先逐一核对：上面每个 IN_PROGRESS 的任务是否其实已经达成？凡是已达成的，必须现在就调用 updateTaskStatus 将其标记为 DONE"
                            + "（否则系统会每隔 " + PluginConfig.taskLoopIntervalSeconds + " 秒重复唤醒你执行它，浪费算力）。"
                            + "核对完后再继续执行尚未完成的任务。" + statusContext);
                    if (response != null && !response.trim().isEmpty()) {
                        logger.info("[TaskLoop] AI 思考结果: {}", response);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in task loop", e);
            }
        }, 30, PluginConfig.taskLoopIntervalSeconds, TimeUnit.SECONDS); // 启动后延迟 30 秒开始
    }

    @Override
    public void onDisable() {
        logger.info("Disabling XinClawPlugin.");
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
}
