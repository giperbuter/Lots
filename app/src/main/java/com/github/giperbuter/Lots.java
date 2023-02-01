package com.github.giperbuter;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.io.File;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

// manages the data.yml file
class DataFileManager {
  // yaml config file
  FileConfiguration config;
  File file;

  DataFileManager(File dataFolder) {
    try {
      file = new File(dataFolder + File.separator + "Data.yml");
      config = YamlConfiguration.loadConfiguration(file);
      if (!file.exists()) {
        file.createNewFile();
      }
    } catch (Exception e) {
      System.err.println(e);
    }
  }

  public void setScore(int amount, String name) {
    config.set(name + ".score-amount", amount);
  }

  public int getScore(String name) {
    return config.getInt(name + ".score-amount");
  }

  // get the x coord of the center of a players's lot
  public int getLotLocationX(String name) {
    return config.getInt(name + ".lot-location.x");
  }

  // get the z coord of the center of a players's lot
  public int getLotLocationZ(String name) {
    return config.getInt(name + ".lot-location.z");
  }

  // @SuppressWarnings("unchecked")
  // public List<String> getMergedLots(String name) {
  // return (List<String>) config.getList(name + ".merged-lots");
  // }

  public String getTeam(String name) {
    return config.getString(name + ".team");
  }

  // public void addMergedLots(String name, String plToAdd) {
  // var list = getMergedLots(name);
  // list.add(plToAdd);
  // config.set(name + ".merged-lots", list);
  // }

  public Set<String> getNames() {
    return config.getKeys(false);
  }

  public void save() {
    try {
      config.save(file);
    } catch (Exception e) {
      System.err.println(e);
    }
  }
}

// manages the scoreboard
class ScoreboardManager {
  public static Scoreboard board;
  public static Objective obj;
  private static DataFileManager data;
  private static int blueSum = 0;
  private static int redSum = 0;
  private static Team blueTeam = null;
  private static Team redTeam = null;

  ScoreboardManager(DataFileManager d) {
    // init
    data = d;
    board = Bukkit.getServer().getScoreboardManager().getNewScoreboard();
    // teams
    blueTeam = board.registerNewTeam("Blue");
    redTeam = board.registerNewTeam("Red");
    // set teams colors
    blueTeam.color(NamedTextColor.BLUE);
    redTeam.color(NamedTextColor.RED);
    // assign each player a team
    for (String name : data.getNames()) {
      if (data.getTeam(name).equals("blue")) {
        blueTeam.addPlayer(Bukkit.getOfflinePlayer(name));
      } else {
        redTeam.addPlayer(Bukkit.getOfflinePlayer(name));
      }
    }
    // sidplay who is winning
    blueTeam.addEntry("Blue Sum");
    redTeam.addEntry("Red Sum");
    // to display their xp
    TextComponent cmp = Component.text("XP").color(TextColor.fromHexString("#6C96BF"));
    obj = board.registerNewObjective("xp", Criteria.XP, cmp);
    obj.setDisplaySlot(DisplaySlot.SIDEBAR);
    for (String name : data.getNames()) {
      
    }
  }

  public void showToPlayer(Player player) {
    player.setScoreboard(board);
  }

  public void updateScore() {
    for (String pl : data.getNames()) {
      if (blueTeam.hasPlayer(Bukkit.getOfflinePlayer(pl))) {
        blueSum += data.getScore(pl);
      } else {
        redSum += data.getScore(pl);
      }
    }
    obj.getScore("Blue Sum").setScore(blueSum);
    obj.getScore("Red Sum").setScore(redSum);
  }

}

public final class Lots extends JavaPlugin implements Listener {
  // used to seperate functionality
  private static DataFileManager config = null;
  private static ScoreboardManager scoreboard = null;

  // shows the player the msg above the hotbar.
  private void notifyPlayer(Player player, String msg) {
    Component message = Component
        .text(msg)
        .color(TextColor.fromHexString("#9FC3E9"));
    Audience.audience(player).sendActionBar(message);
  }

  // return false if loc is outside the lot located at cX, cZ
  private Boolean isInside(int cX, int cZ, Location loc) {
    if (loc.x() < cX + 200 &&
        loc.x() > cX - 200 &&
        loc.z() < cZ + 200 &&
        loc.z() > cZ - 200) {
      return true;
    }
    return false;
  }

  // returns true if the player is outside his lot (including the lots he's merged
  // with).
  private Boolean isOutsideAllLots(Player player, Location loc) {
    // check if inside player's original lot
    if (isInside(config.getLotLocationX(player.getName()),
        config.getLotLocationZ(player.getName()), loc)) {
      return false;
    }
    // check for every lot he's merged with
    // if (config.getMergedLots(player.getName()) != null) {
    // for (var name : config.getMergedLots(player.getName())) {
    // if (isInside(config.getLotLocationX(name),
    // config.getLotLocationZ(name), loc)) {
    // return false;
    // }
    // }
    // }
    return true;
  }

  // returns the player the lot the location is in belongs to
  private String getLot(Location loc) {
    for (var pl : config.getNames()) {
      if (isInside(config.getLotLocationX(pl), config.getLotLocationZ(pl), loc)) {
        return pl;
      }
    }
    return "";
  }

  // get Location of the center of the players's lot(uses the world the player's
  // already in).
  private Location getLocation(Player pl) {
    return new Location(pl.getWorld(), config.getLotLocationX(pl.getName()), 63,
        config.getLotLocationZ(pl.getName()));
  }

  // get Location of the center of the players's lot in the provided world.
  private Location getLocation(Player pl, World world) {
    return new Location(world, config.getLotLocationX(pl.getName()), 63,
        config.getLotLocationZ(pl.getName()));
  }

  // get Location of the center of the players's lot in the provided world.
  private Location getLocation(String pl, World world) {
    return new Location(world, config.getLotLocationX(pl), 63,
        config.getLotLocationZ(pl));
  }

  // generates the nether platform at the center of the player's lot in the
  // specified world.
  private void generatePlatform(World world, Player player) {
    var x = getLocation(player).getBlockX();
    var z = getLocation(player).getBlockZ();

    for (int i = -1; i <= 1; i++) {
      for (int j = -1; j <= 1; j++) {
        world.getBlockAt(i, 62, j).setType(Material.NETHERRACK);
        world.getBlockAt(i, 63, j).setType(Material.AIR);
        world.getBlockAt(i, 64, j).setType(Material.AIR);
        world.getBlockAt(i, 65, j).setType(Material.AIR);
      }
    }

    world.getBlockAt(x - 1, 62, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x, 62, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x + 1, 62, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x - 1, 63, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x, 63, z).setType(Material.NETHER_PORTAL);
    world.getBlockAt(x + 1, 63, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x - 1, 64, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x, 64, z).setType(Material.NETHER_PORTAL);
    world.getBlockAt(x + 1, 64, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x - 1, 65, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x, 65, z).setType(Material.OBSIDIAN);
    world.getBlockAt(x + 1, 65, z).setType(Material.OBSIDIAN);
  }

  // generate platform's portal blocks to prevent stucking in the nether without
  // portal and directing them
  private void generatePortalBlocks(World world, Player player) {
    world.getBlockAt(getLocation(player).getBlockX(), 63,
        getLocation(player).getBlockZ()).setType(Material.NETHER_PORTAL);
    world.getBlockAt(getLocation(player).getBlockX(), 64,
        getLocation(player).getBlockZ()).setType(Material.NETHER_PORTAL);
  }

  // initialization
  @Override
  public void onEnable() {
    // initialize classess
    Bukkit.getPluginManager().registerEvents(this, this);
    config = new DataFileManager(getDataFolder());
    scoreboard = new ScoreboardManager(config);
    // update xp
    BukkitTask task = new BukkitRunnable() {
      public void run() {
        for (var pl : getServer().getOnlinePlayers()) {
          config.setScore(pl.getTotalExperience(), pl.getName());
        }
        config.save();
      }
    }.runTaskTimer(this, 0L, 1L);

    // scoreboard.updateScore();
    // register commands
    // this.getCommand("submit").setExecutor(new Submit(Material.GOLDEN_APPLE));
    // this.getCommand("merge").setExecutor(new Merge());
  }

  // @EventHandler
  // public void onXpChange(PlayerExpChangeEvent event) {
  // config.setScore(event.getPlayer().getTotalExperience() + event.getAmount(),
  // event.getPlayer().getName());
  // scoreboard.updateScore();
  // }

  // @EventHandler
  // public void onPlayerDie(PlayerDeathEvent event) {
  // config.setScore(0, event.getPlayer().getName());
  // scoreboard.updateScore();
  // }

  // set player's spawn point to lot when first joined the server
  @EventHandler
  public void onPlayerFirstJoin(PlayerSpawnLocationEvent event) {
    // not first time
    if (!event.getPlayer().hasPlayedBefore()) {
      // generate at the center of player's lot
      event.setSpawnLocation(getLocation(event.getPlayer()));
      // the player allowed to change the platform so generate only once
      generatePlatform(getServer().getWorld("world_nether"), event.getPlayer());
    }
  }

  // prevent breaking the plugin with null exceptions when the player is not
  // registered in the config file.
  public void onPlayerLogin(PlayerLoginEvent event) {
    if (!config.getNames().contains(event.getPlayer().getName())) {
      Component message = Component
          .text("You are not listed in the Lots.yml file, contact the server administrator for help")
          .color(TextColor.fromHexString("#9FC3E9"));
      event.kickMessage(message);
    }
  }

  // set the lot border and welcome message
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent event) {
    var player = event.getPlayer();
    // welcome message
    notifyPlayer(player,
        "welcome, your lot is located at " + (int) getLocation(player).x() + " " + (int) getLocation(player).z()
            + ".");
    // let player to see scoreboard
    scoreboard.showToPlayer(player);

  }

  // knockback player if hes moving outside his lots
  @EventHandler
  public void onPlayerMove(PlayerMoveEvent event) {
    var player = event.getPlayer();
    if (isOutsideAllLots(player, event.getTo())) {
      // veloctiy vector for knockback
      var s = getLocation(player);
      var e = player.getLocation();
      var vec = new Vector(s.x() - e.x(), 0, s.z() - e.z()).normalize();
      player.setVelocity(vec);
    }
  }

  // prevent right clicking anything outside lot
  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    // for not showinf if hitting air
    if (event.getInteractionPoint() != null) {
      if (isOutsideAllLots(event.getPlayer(), event.getInteractionPoint())) {
        event.setCancelled(true);
        notifyPlayer(event.getPlayer(), "Can't interact with anything outside your lot.");
      }
    }
  }

  // prevent right destroying anything outside lot
  @EventHandler
  public void obPlayerDestroyBlock(BlockBreakEvent event) {
    if (isOutsideAllLots(event.getPlayer(), event.getBlock().getLocation())) {
      event.setCancelled(true);
      notifyPlayer(event.getPlayer(), "Can't interact with anything outside your lot.");
    }
  }

  // unless the player has a bed or anchor, he will respawn at center of his lot
  @EventHandler
  public void onPlayerRespawn(PlayerRespawnEvent event) {
    // does the player have a bed or an anchor to respawn to
    if (!event.isAnchorSpawn() && !event.isBedSpawn()) {
      // if so, respawn at center of his original lot
      event.setRespawnLocation(getLocation(event.getPlayer()));
    }
  }

  // preventing teleporting to outside the lot
  @EventHandler
  public void onPlayerTeleport(PlayerTeleportEvent event) {
    // is the player outside of lot
    if (isOutsideAllLots(event.getPlayer(), event.getTo())) {
      var player = event.getPlayer();
      notifyPlayer(player, "Can't teleport to outside your lot");
      event.setCancelled(true);
    }
  }

  // when an entity enters a portal tp to platform
  @EventHandler
  public void onEntityEnterPortal(EntityPortalEvent event) {
    if (event.getFrom().getWorld().getEnvironment() == Environment.NORMAL) {
      var pl = getLot(event.getFrom());
      // teleport only to platform and stop creating a portal
      event.getEntity().teleport(getLocation(pl, getServer().getWorld("world_nether")));
      // update portal blocks
      generatePortalBlocks(event.getTo().getWorld(), getServer().getPlayer(pl));
    }
  }

  // when player enters a portal prevent generating a portal in the nether and
  // teleport to the platform
  @EventHandler
  public void onPlayerEnterPortal(PlayerPortalEvent event) {
    // only if you go from overworld to nether
    if (event.getFrom().getWorld().getEnvironment() == Environment.NORMAL) {
      // teleport to platform
      var player = event.getPlayer();
      player.teleport(getLocation(player, getServer().getWorld("world_nether")));
      // update portal blocks
      generatePortalBlocks(event.getTo().getWorld(), player);
      // prevnt generating portals in the nether
      event.setCanCreatePortal(false);
      // to not link to the platform
      event.setSearchRadius(0);
    }
  }

  // prevent creating portals in the nether
  @EventHandler
  public void onPortalCreation(PortalCreateEvent event) {
    // if creating in the nether
    if (event.getWorld().getEnvironment() == Environment.NETHER) {
      // prevent creatian
      event.setCancelled(true);
      // notify and explain
      if (event.getEntity() instanceof Player) {
        // generate platform's portal blocks to prevent stucking in the nether without
        // portal and directing them
        generatePortalBlocks(event.getWorld(), (Player) event.getEntity());
        // notify the player about this rule
        int x = (int) getLocation((Player) event.getEntity()).x();
        int z = (int) getLocation((Player) event.getEntity()).z();
        notifyPlayer((Player) event.getEntity(),
            "Can't create portals in the nether due to 1:8 block ratio, use the default portal located at " + x + " 63 "
                + z + ".");
      }
    }
  }

  // count and delete the specified item material from the inventory and add to
  // the scoreboard
  // public class Submit implements CommandExecutor {
  // // the metrial to count and delete
  // Material itemMaterial = Material.AIR;

  // Submit(Material item) {
  // itemMaterial = item;
  // }

  // /execute
  // @Override
  // public boolean onCommand(CommandSender sender, Command command, String label,
  // String[] args) {
  // // to prevent a command block and such from calling it
  // if (sender instanceof Player) {
  // Player player = (Player) sender;
  // int amount = 0;
  // // count from inventory
  // var items = player.getInventory().all(itemMaterial);
  // for (var set : items.entrySet()) {
  // amount += set.getValue().getAmount();
  // }
  // // remove from inventory
  // player.getInventory().remove(itemMaterial);
  // // save it to yaml file
  // config.addScore(amount, sender.getName());
  // config.save();
  // // update scoreboard
  // scoreboard.updateScore(sender.getName());
  // return true;
  // }
  // return false;
  // }
  // }

  // kerge two lots: two people who want to merge need to call this command with
  // each others names in less than ten seconds interval. merging lots means that
  // the two people will be able to move freely between the lots and
  // interact/destroy/build anything in both lots
  // public class Merge implements CommandExecutor {
  // // struct containing a call info
  // private class Call {
  // public String caller;
  // public String called;
  // public Long time;

  // public Call(String cr, String cd, Long t) {
  // caller = cr;
  // called = cd;
  // time = t;
  // }
  // }

  // // stores all unanswered requests.
  // private static Set<Call> calls = new HashSet<Call>();

  // // /merge <PLayerName>
  // @Override
  // public boolean onCommand(CommandSender sender, Command command, String label,
  // String[] args) {
  // // checks if its a proper function call
  // if (sender instanceof Player && args[0] instanceof String &&
  // config.getNames().contains(args[0])) {
  // // check basic problems
  // if (sender.getName() == args[0]) {
  // notifyPlayer((Player) sender, "Can't merge with yourself");
  // return false;
  // }
  // if (config.getMergedLots(sender.getName()).contains(args[0])) {
  // notifyPlayer((Player) sender, "Already merged with " + args[0]);
  // return false;
  // }
  // // checks if its an answer request
  // for (var request : calls) {
  // if (request.called.equals(args[0]) &&
  // request.caller.equals(sender.getName()) &&
  // System.currentTimeMillis() - request.time <= 10000) {
  // // try to merge and send result
  // Player pl2 = getServer().getPlayer(args[0]);
  // String response = mergeLots((Player) sender, pl2);
  // notifyPlayer((Player) sender, response);
  // notifyPlayer(pl2, response);
  // // to prevnt adding to unanswered calls list.
  // return true;
  // }
  // }
  // // add to calls because its a request
  // calls.add(new Call(sender.getName(), args[0], System.currentTimeMillis()));

  // return true;
  // }

  // return false;
  // }
  // }

}
