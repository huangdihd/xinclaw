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
