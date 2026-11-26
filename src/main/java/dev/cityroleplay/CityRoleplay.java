package dev.cityroleplay;

import dev.cityroleplay.admin.AdminGui;
import dev.cityroleplay.chat.ChatPromptManager;
import dev.cityroleplay.command.CommandManager;
import dev.cityroleplay.config.ConfigManager;
import dev.cityroleplay.config.Messages;
import dev.cityroleplay.criminal.CriminalService;
import dev.cityroleplay.db.Database;
import dev.cityroleplay.economy.EconomyManager;
import dev.cityroleplay.gui.GuiManager;
import dev.cityroleplay.integration.IntegrationManager;
import dev.cityroleplay.integration.LuckPermsHook;
import dev.cityroleplay.listener.PlayerListener;
import dev.cityroleplay.police.ArrestManager;
import dev.cityroleplay.police.DutyZoneManager;
import dev.cityroleplay.police.GraveManager;
import dev.cityroleplay.police.PoliceDashboardGui;
import dev.cityroleplay.police.PoliceService;
import dev.cityroleplay.police.ShopGui;
import dev.cityroleplay.prison.PrisonManager;
import dev.cityroleplay.profile.ProfileService;
import dev.cityroleplay.robbery.RobberyManager;
import dev.cityroleplay.selection.SelectionListener;
import dev.cityroleplay.selection.SelectionManager;
import dev.cityroleplay.sell.SellGui;
import dev.cityroleplay.sell.SellService;
import dev.cityroleplay.tools.ArrestBatonBehavior;
import dev.cityroleplay.tools.ArrestRopeBehavior;
import dev.cityroleplay.tools.CrossbowBehavior;
import dev.cityroleplay.tools.GraveShovelBehavior;
import dev.cityroleplay.tools.InvestigationBrushBehavior;
import dev.cityroleplay.tools.NativeArrestRopeAdapter;
import dev.cityroleplay.tools.ToolListener;
import dev.cityroleplay.tools.ToolRegistry;
import dev.cityroleplay.tools.ToolService;
import dev.cityroleplay.util.ItemUtil;
import dev.cityroleplay.util.Scheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public final class CityRoleplay extends JavaPlugin {

    private ConfigManager config;
    private Messages messages;
    private Scheduler scheduler;
    private Database database;
    private ProfileService profiles;
    private EconomyManager economy;
    private LuckPermsHook luckPerms;
    private IntegrationManager integrations;
    private PoliceService police;
    private CriminalService criminals;
    private GraveManager graves;
    private PrisonManager prison;
    private ArrestManager arrests;
    private DutyZoneManager dutyZones;
    private RobberyManager robberies;
    private ToolRegistry toolRegistry;
    private ToolService toolService;
    private ShopGui shopGui;
    private PoliceDashboardGui policeDashboard;
    private SellService sellService;
    private SellGui sellGui;
    private GuiManager guiManager;
    private SelectionManager selections;
    private ChatPromptManager prompts;
    private AdminGui adminGui;

    @Override
    public void onEnable() {
        ItemUtil.init(this);
        scheduler = new Scheduler(this);
        config = new ConfigManager(this);
        config.loadAll();
        messages = new Messages(config);

        database = new Database(new File(getDataFolder(), "database.db"), getLogger());
        try {
            database.initialize();
        } catch (SQLException exception) {
            getLogger().severe("Could not initialise SQLite: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        profiles = new ProfileService(this, database,
                config.get(ConfigManager.ECONOMY).getDouble("economy.starting-balance", 1000.0));
        profiles.loadAll();

        economy = new EconomyManager(this, config, profiles, database);
        luckPerms = new LuckPermsHook(this);
        integrations = new IntegrationManager(this, config, luckPerms, economy);
        integrations.setup();

        police = new PoliceService(this, config, messages, profiles, economy, luckPerms);
        criminals = new CriminalService(this, config, messages, profiles);

        graves = new GraveManager(this, database, messages, police, economy, scheduler);
        graves.load();

        prison = new PrisonManager(this, config, messages, profiles, database, scheduler, criminals);
        prison.start();

        arrests = new ArrestManager(this, config, messages, police, prison, profiles,
                database, criminals, economy, scheduler);
        arrests.start();
        getServer().getPluginManager().registerEvents(arrests, this);

        dutyZones = new DutyZoneManager(this, config, messages, profiles, police, economy,
                database, luckPerms);
        dutyZones.load();
        dutyZones.start();

        robberies = new RobberyManager(this, config, messages, profiles, economy, database,
                criminals, police, dutyZones);
        robberies.load();
        robberies.start();

        toolRegistry = new ToolRegistry(this, config);
        toolRegistry.registerBehavior(new GraveShovelBehavior(this, graves, messages));
        toolRegistry.registerBehavior(new InvestigationBrushBehavior(messages, economy, luckPerms,
                criminals, profiles));
        ArrestRopeBehavior rope = new ArrestRopeBehavior(messages, arrests);
        rope.registerAdapter(new NativeArrestRopeAdapter(arrests));
        toolRegistry.registerBehavior(rope);
        toolRegistry.registerBehavior(new CrossbowBehavior(messages, police));
        toolRegistry.registerBehavior(new ArrestBatonBehavior(arrests));
        toolRegistry.load();

        toolService = new ToolService(this, toolRegistry, database, economy, police, messages, scheduler);
        toolService.loadSerials();

        shopGui = new ShopGui(config, messages, economy, toolRegistry, toolService);
        policeDashboard = new PoliceDashboardGui(config, messages, profiles, economy, police,
                dutyZones, shopGui, database, scheduler);

        sellService = new SellService(config, messages, economy);
        sellGui = new SellGui(this, sellService, messages);
        sellGui.register();

        guiManager = new GuiManager(this);
        guiManager.register();
        selections = new SelectionManager();
        getServer().getPluginManager().registerEvents(new SelectionListener(selections, messages), this);
        prompts = new ChatPromptManager(messages, scheduler);
        getServer().getPluginManager().registerEvents(prompts, this);
        getServer().getPluginManager().registerEvents(new ToolListener(toolService, toolRegistry, messages), this);
        getServer().getPluginManager().registerEvents(robberies, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, config, messages,
                profiles, police, criminals, prison, dutyZones, economy), this);

        adminGui = new AdminGui(this);
        new CommandManager(this).register();

        int saveSeconds = Math.max(10, config.get(ConfigManager.CONFIG).getInt("save-interval-seconds", 60));
        scheduler.timer(profiles::flushDirty, 20L * saveSeconds, 20L * saveSeconds);

        getLogger().info("CityRoleplay enabled: " + profiles.all().size() + " profiles, "
                + dutyZones.zones().size() + " duty zones, " + robberies.zones().size()
                + " robbery zones, " + toolRegistry.all().size() + " tools.");
    }

    @Override
    public void onDisable() {
        if (robberies != null) {
            robberies.stop();
        }
        if (dutyZones != null) {
            dutyZones.stop();
        }
        if (arrests != null) {
            arrests.shutdown();
        }
        if (prison != null) {
            prison.stop();
        }
        if (profiles != null) {
            profiles.saveAll();
        }
        if (database != null) {
            database.close();
        }
    }

    public void reloadEverything() {
        config.reloadAll();
        dutyZones.reload();
        dutyZones.start();
        robberies.stop();
        robberies.load();
        robberies.start();
        toolRegistry.load();
        sellService.reload();
        prison.start();
        toolService.loadSerials();
        integrations.setup();
    }

    public ConfigManager config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    public Database database() {
        return database;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public EconomyManager economy() {
        return economy;
    }

    public LuckPermsHook luckPerms() {
        return luckPerms;
    }

    public IntegrationManager integrations() {
        return integrations;
    }

    public PoliceService police() {
        return police;
    }

    public CriminalService criminals() {
        return criminals;
    }

    public GraveManager graves() {
        return graves;
    }

    public PrisonManager prison() {
        return prison;
    }

    public ArrestManager arrests() {
        return arrests;
    }

    public DutyZoneManager dutyZones() {
        return dutyZones;
    }

    public RobberyManager robberies() {
        return robberies;
    }

    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    public ToolService toolService() {
        return toolService;
    }

    public ShopGui shopGui() {
        return shopGui;
    }

    public PoliceDashboardGui policeDashboard() {
        return policeDashboard;
    }

    public SellService sellService() {
        return sellService;
    }

    public SellGui sellGui() {
        return sellGui;
    }

    public GuiManager guiManager() {
        return guiManager;
    }

    public SelectionManager selections() {
        return selections;
    }

    public ChatPromptManager prompts() {
        return prompts;
    }

    public AdminGui adminGui() {
        return adminGui;
    }
}
