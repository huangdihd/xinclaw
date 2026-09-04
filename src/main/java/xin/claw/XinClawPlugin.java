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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import xin.claw.listeners.PrivateMessageListener;
import xin.claw.trackers.DimensionTracker;
import xin.claw.trackers.InventoryTracker;
import xin.claw.tasks.Task;
import xin.claw.tools.MovementTools;

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
    public boolean currentMovementTaskIsImplicit = false;
    public org.joml.Vector3i currentMovementGoal = null;
    private final AtomicLong taskLoopGeneration = new AtomicLong();
    private final AtomicBoolean taskLoopSuppressed = new AtomicBoolean();

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

            Bot.INSTANCE.getPluginManager().events().registerEvents(new xin.claw.listeners.ItemPickupListener(), this);
            logger.info("ItemPickupListener initialized.");

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
                if (taskLoopSuppressed.get()) return;
                if (agentManager.isProcessing()) return;
                long capturedGeneration = taskLoopGeneration.get();

                List<Task> tasks = agentManager.getTaskManager().getTasks();
                boolean hasPending = hasPendingTasks(tasks);
                boolean onlyImplicit = onlyPendingTaskIsImplicit(
                    tasks, currentMovementTaskId, currentMovementTaskIsImplicit);

                if (hasPending) {
                    String statusContext = "";
                    if (MovementSync.INSTANCE != null) {
                        boolean isMoving = MovementSync.INSTANCE.getMovementController().getCurrentMovement() != null;
                        org.joml.Vector3i goal = effectiveMovementGoal(
                            MovementSync.INSTANCE.getActiveGoal(), currentMovementGoal);
                        
                        if (goal != null) {
                            org.joml.Vector3d currentPos = MovementSync.INSTANCE.position.get();
                            double dist = currentPos.distance(new org.joml.Vector3d(goal.x + 0.5, goal.y, goal.z + 0.5));
                            
                            if (dist < 2.0) {
                                // 到达目标
                                MovementSync.INSTANCE.cancelNavigation();
                                
                                // 自动融合任务系统：如果绑定了任务，则自动标记为完成
                                if (currentMovementTaskId != null && agentManager != null && agentManager.getTaskManager() != null) {
                                    String completedTaskId = currentMovementTaskId;
                                    boolean implicit = currentMovementTaskIsImplicit;
                                    boolean completed = MovementTools.completeMovementTask(
                                        agentManager.getTaskManager(), completedTaskId, implicit);
                                    statusContext = implicit
                                        ? String.format("\n[状态提示] 机器人已成功到达寻路目标 (%d, %d, %d)！系统已完成并清理内部续航任务。请立即检查环境并继续原始用户目标。", goal.x, goal.y, goal.z)
                                        : String.format("\n[状态提示] 机器人已成功到达寻路目标 (%d, %d, %d)！对应任务(ID: %s)%s。请检查环境并执行下一步操作。", goal.x, goal.y, goal.z, completedTaskId, completed ? "已标记为 DONE" : "未找到，未能自动标记");
                                    currentMovementTaskId = null;
                                    currentMovementTaskIsImplicit = false;
                                    currentMovementGoal = null;
                                } else {
                                    statusContext = String.format("\n[状态提示] 机器人已成功到达寻路目标 (%d, %d, %d)！请执行下一步操作，如果该步骤的任务已完成，请记得 updateTaskStatus。", goal.x, goal.y, goal.z);
                                }
                            } else if (!isMoving) {
                                // 静止但未到达目标 (意味着卡死在路上了)
                                MovementSync.INSTANCE.cancelNavigation();
                                statusContext = String.format("\n[状态提示] 寻路已强制中断！你设定了前往 (%d, %d, %d) 的目标，但机器人目前被完全卡住静止 (距离目标还有 %.1f 格)。为防止死循环，系统已自动取消该次寻路。请务必检查周围环境(方块)，必要时挖掘障碍物、搭桥或换个坐标重新寻路。", goal.x, goal.y, goal.z, dist);
                            } else {
                                statusContext = String.format("\n[状态提示] 机器人目前正在向寻路目标 (%d, %d, %d) 移动中，当前距离目标还有 %.1f 格。你可以通过 stopWalking 随时打断寻路，或查阅其它工具。如果不打算打断移动，请仅简短回复正在路上即可。", goal.x, goal.y, goal.z, dist);
                                if (onlyImplicit) return;
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
                    if (taskLoopSuppressed.get() || !isTaskLoopGenerationCurrent(capturedGeneration)) return;
                    logger.info("[TaskLoop] 发现正在进行中的任务，唤醒 AI 继续工作...");
                    String response = agentManager.processMessageIf(
                            "[SYSTEM_TICK] 你有正在进行中的任务。当前未完成任务清单:\n" + pendingTasks
                            + "\n请先逐一核对：上面每个 IN_PROGRESS 的任务是否其实已经达成？凡是已达成的，必须现在就调用 updateTaskStatus 将其标记为 DONE"
                            + "（否则系统会每隔 " + PluginConfig.taskLoopIntervalSeconds + " 秒重复唤醒你执行它，浪费算力）。"
                            + "核对完后再继续执行尚未完成的任务。" + statusContext,
                            () -> !taskLoopSuppressed.get()
                                && isTaskLoopGenerationCurrent(capturedGeneration));
                    if (response != null && !response.trim().isEmpty()) {
                        logger.info("[TaskLoop] AI 思考结果: {}", response);
                    }
                }
            } catch (Exception e) {
                logger.error("Error in task loop", e);
            }
        }, 30, PluginConfig.taskLoopIntervalSeconds, TimeUnit.SECONDS); // 启动后延迟 30 秒开始
    }

    static boolean hasPendingTasks(List<Task> tasks) {
        return tasks.stream().anyMatch(task -> task.getStatus() != Task.Status.DONE);
    }

    static boolean onlyPendingTaskIsImplicit(
            List<Task> tasks,
            String movementTaskId,
            boolean movementTaskIsImplicit) {
        if (!movementTaskIsImplicit || movementTaskId == null) return false;
        List<Task> pending = tasks.stream()
            .filter(task -> task.getStatus() != Task.Status.DONE)
            .toList();
        return pending.size() == 1 && pending.get(0).getId().equalsIgnoreCase(movementTaskId);
    }

    static org.joml.Vector3i effectiveMovementGoal(
            org.joml.Vector3i runtimeGoal,
            org.joml.Vector3i storedGoal) {
        return runtimeGoal != null ? runtimeGoal : storedGoal;
    }

    long taskLoopGeneration() {
        return taskLoopGeneration.get();
    }

    boolean isTaskLoopGenerationCurrent(long captured) {
        return taskLoopGeneration.get() == captured;
    }

    public void invalidateTaskLoopWork() {
        taskLoopGeneration.incrementAndGet();
        if (currentMovementTaskIsImplicit && currentMovementTaskId != null
                && agentManager != null && agentManager.getTaskManager() != null) {
            agentManager.getTaskManager().removeTask(currentMovementTaskId);
        }
        currentMovementTaskId = null;
        currentMovementTaskIsImplicit = false;
        currentMovementGoal = null;
    }

    public void suspendTaskLoopWork() {
        taskLoopSuppressed.set(true);
        invalidateTaskLoopWork();
    }

    public void resumeTaskLoopWork() {
        taskLoopGeneration.incrementAndGet();
        taskLoopSuppressed.set(false);
    }

    boolean isTaskLoopWorkSuppressed() {
        return taskLoopSuppressed.get();
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
