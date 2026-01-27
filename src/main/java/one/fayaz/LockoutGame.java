package one.fayaz;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class LockoutGame {
    public static final LockoutGame INSTANCE = new LockoutGame();

    private static final Map<UUID, ItemStack[]> LAST_ARMOR = new HashMap<>();

    public enum GameMode {
        DEATH,
        KILLS,
        ARMOR,
        ADVANCEMENTS,
        FOODS,
        BREED,
        MIXED
    }

    public enum DeathMatchMode {
        MESSAGE,
        SOURCE
    }

    public enum ArmorMode {
        SET,
        PIECE
    }

    private Set<GameMode> mixedModes = new HashSet<>(Arrays.asList(
            GameMode.DEATH,
            GameMode.KILLS,
            GameMode.ARMOR,
            GameMode.ADVANCEMENTS,
            GameMode.FOODS,
            GameMode.BREED
    ));

    public boolean addMixedMode(GameMode mode) {
        return mixedModes.add(mode);
    }

    public boolean removeMixedMode(GameMode mode) {
        return mixedModes.remove(mode);
    }

    public Set<GameMode> getMixedModes() {
        return new HashSet<>(mixedModes);
    }
    private boolean active = false;
    private boolean paused = false;
    private boolean resetWorld = false;
    private String pausedPlayerName = "";
    private int countdownTicks = 0;
    private boolean isCountingDown = false;
    private int armorCheckTicks = 0; // For periodic armor checking
    private int goal = 5;
    private GameMode mode = GameMode.MIXED;
    private ArmorMode armorMode = ArmorMode.SET;
    private DeathMatchMode deathMatchMode = DeathMatchMode.SOURCE;
    private static boolean doSwitch = false;
    private static boolean snarkyMessages = true;
    private final Map<UUID, PlayerEntry> players = new LinkedHashMap<>();
    private final Set<String> claimedItems = new HashSet<>();

    private Vec3 customSpawnPos = null;
    private ResourceKey<Level> customSpawnDimension = null;

    public void setGoal(int goal) {
        this.goal = goal;
    }

    public int getGoal() {
        return goal;
    }

    public GameMode getMode() {
        return mode;
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public void setResetWorld(boolean resetWorld) {
        this.resetWorld = resetWorld;
    }

    public void setDoSwitch(boolean doSwitch) {
        this.doSwitch = doSwitch;
    }

    public void setSnarkyMessages(boolean snarkyMessages) {
        this.snarkyMessages = snarkyMessages;
    }

    public void setArmorMode(ArmorMode armorMode) {
        this.armorMode = armorMode;
    }

    public void setDeathMatchMode(DeathMatchMode deathMatchMode) {
        this.deathMatchMode = deathMatchMode;
    }

    public ArmorMode getArmorMode() {
        return armorMode;
    }

    public DeathMatchMode getDeathMatchMode() {
        return deathMatchMode;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getPausedPlayerName() {
        return pausedPlayerName;
    }

    public boolean addPlayer(ServerPlayer player, int color) {
        if (active) {
            player.sendSystemMessage(Component.literal("❌ Cannot add player(s) while game is active!").withStyle(style -> style.withColor(0xFF5555)));
            return false;
        }

        UUID uuid = player.getUUID();
        if (players.containsKey(uuid)) {
            player.sendSystemMessage(Component.literal("❌ Player(s) already added!").withStyle(style -> style.withColor(0xFF5555)));
            return false;
        }

        players.put(uuid, new PlayerEntry(uuid, player.getName().getString(), color));
        return true;
    }

    public boolean modifyPlayer(ServerPlayer player, int color) {
        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);

        if (entry == null) {
            return false;
        }

        PlayerEntry newEntry = new PlayerEntry(uuid, entry.getName(), color, entry.getClaims());
        players.put(uuid, newEntry);

        if (active) {
            LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
        }

        return true;
    }

    public boolean removePlayer(ServerPlayer player) {
        if (active) {
            return false;
        }

        UUID uuid = player.getUUID();
        return players.remove(uuid) != null;
    }

    public void syncToPlayer(ServerPlayer player) {
        LockoutNetworking.sendToPlayer(player, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public int canStart() {
        if (goal < 1) {
            return -1;
        }
        return players.size();
    }

    public void resetLevel(MinecraftServer server) {
        // Apply reset to every loaded level (Overworld, Nether, End)
        for (ServerLevel level : server.getAllLevels()) {
            // Time reset (WIP)
//            level.serverLevelData.setDayTime(1000L);

            // Weather Reset
            level.resetWeatherCycle();

            // Kill everything except players
            level.getAllEntities().forEach(entity -> {
                if (!(entity instanceof ServerPlayer)) {
                    entity.discard();
                }
            });

            // Force chunk tick cleanup
            level.getChunkSource().tick(() -> true, true);
        }
    }

    public void start(MinecraftServer server, GameMode mode) {
        if (canStart() < 2) {
            broadcastToServer(server, Component.literal("🎮 FAILED TO START").withStyle(style -> style.withColor(0xFF5555).withBold(true)));
            return;
        }

        this.active = true;
        this.mode = mode;
        this.paused = false;
        this.pausedPlayerName = "";
        this.claimedItems.clear();
        this.isCountingDown = true;
        this.countdownTicks = 60;
        this.armorCheckTicks = 0;

        for (PlayerEntry entry : players.values()) {
            entry.getClaims().clear();
        }

        for (UUID uuid : players.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                preparePlayer(player);
                freezePlayer(player);
            }
        }

        if (this.resetWorld) {
            resetLevel(server);
        }

        String modeName = switch (mode) {
            case DEATH -> "Death";
            case KILLS -> "Kills";
            case ARMOR -> "Armor Sets";
            case ADVANCEMENTS -> "Advancements";
            case FOODS -> "Foods";
            case BREED -> "Breed";
            case MIXED -> "Mixed";
        };

        broadcastToServer(server, Component.literal("🎮 " + modeName + " Lockout Starting...").withStyle(style -> style.withColor(0xFFFF55).withBold(true)));
        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public static void handleSwitch(ServerPlayer trigger) {
        if (!doSwitch) return;

        MinecraftServer server = trigger.level().getServer();
        if (server == null) return;

        // Collect Lockout players in order
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID uuid : LockoutGame.INSTANCE.getPlayers().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                players.add(p);
            }
        }

        int count = players.size();
        if (count < 2) return;

        // === SNAPSHOT ALL PLAYERS ===
        List<PlayerSnapshot> snapshots = new ArrayList<>();

        for (ServerPlayer p : players) {
            PlayerSnapshot s = new PlayerSnapshot();

            s.level = p.level();
            s.pos = p.position();
            s.yaw = p.getYRot();
            s.pitch = p.getXRot();

            s.inventory = new ArrayList<>();
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                s.inventory.add(p.getInventory().getItem(i).copy());
            }

            s.effects = new ArrayList<>();
            for (MobEffectInstance effect : p.getActiveEffects()) {
                s.effects.add(new MobEffectInstance(effect));
            }

            s.xpLevel = p.experienceLevel;
            s.xpProgress = p.experienceProgress;
            s.xpTotal = p.totalExperience;

            s.health = p.getHealth();

            s.food = p.getFoodData().getFoodLevel();
            s.saturation = p.getFoodData().getSaturationLevel();

            var respawn = p.getRespawnConfig().respawnData();

            s.respawnPos = respawn.pos();
            s.respawnDim = respawn.dimension();
            s.respawnYaw = respawn.yaw();
            s.respawnPitch = respawn.pitch();
            s.respawnForced = p.getRespawnConfig().forced();

            snapshots.add(s);
        }

        // === APPLY ROTATION (i inherits i+1, last inherits 0) ===
        for (int i = 0; i < count; i++) {
            ServerPlayer target = players.get(i);
            PlayerSnapshot from = snapshots.get((i + 1) % count);

            // Teleport
            ServerLevel targetLevel = (ServerLevel) from.level;

            target.teleportTo(
                    targetLevel,
                    from.pos.x,
                    from.pos.y,
                    from.pos.z,
                    EnumSet.noneOf(Relative.class),
                    from.yaw,
                    from.pitch,
                    true
            );

            // Inventory
            target.getInventory().clearContent();
            for (int slot = 0; slot < from.inventory.size(); slot++) {
                target.getInventory().setItem(slot, from.inventory.get(slot));
            }
            target.getInventory().setChanged();

            // XP
            target.setExperienceLevels(from.xpLevel);
            target.experienceProgress = from.xpProgress;
            target.totalExperience = from.xpTotal;

            // Health
            target.setHealth(from.health);

            // Effects
            target.removeAllEffects();
            for (MobEffectInstance effect : from.effects) {
                target.addEffect(new MobEffectInstance(effect));
            }

            // Food
            target.getFoodData().setFoodLevel(from.food);
            target.getFoodData().setSaturation(from.saturation);

            // Respawn
            target.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(
                                    from.respawnDim,
                                    from.respawnPos,
                                    from.respawnYaw,
                                    from.respawnPitch
                            ),
                            from.respawnForced
                    ),
                    false
            );
        }
    }

    public void tick(MinecraftServer server) {
        // Handle countdown
        if (isCountingDown) {
            countdownTicks--;

            if (countdownTicks == 60) {
                broadcastToServer(server, Component.literal("3").withStyle(style -> style.withColor(0xFF5555).withBold(true)));
            } else if (countdownTicks == 40) {
                broadcastToServer(server, Component.literal("2").withStyle(style -> style.withColor(0xFFAA00).withBold(true)));
            } else if (countdownTicks == 20) {
                broadcastToServer(server, Component.literal("1").withStyle(style -> style.withColor(0xFFFF55).withBold(true)));
            } else if (countdownTicks == 0) {
                broadcastToServer(server, Component.literal("GO!").withStyle(style -> style.withColor(0x55FF55).withBold(true)));
                isCountingDown = false;

                for (UUID uuid : players.keySet()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        unfreezePlayer(player);
                    }
                }
            }
            return;
        }
    }

    public static boolean isArmorPiece(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) return false;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        return switch (slot) {
            case HEAD  -> id.contains("helmet");
            case CHEST -> id.contains("chestplate");
            case LEGS  -> id.contains("leggings");
            case FEET  -> id.contains("boots");
            default    -> false;
        };
    }

    public static String getArmorMaterialName(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        if (id.contains("netherite")) return "Netherite";
        else if (id.contains("diamond"))   return "Diamond";
        else if (id.contains("gold"))      return "Gold";
        else if (id.contains("copper"))      return "Copper";
        else if (id.contains("iron"))      return "Iron";
        else if (id.contains("chainmail")) return "Chainmail";
        else if (id.contains("leather"))   return "Leather";
        else if (id.contains("turtle"))   return "Turtle";
        return "Unknown";
    }

    public void handleArmor(ServerPlayer player) {
        if (!active || paused || isCountingDown ||
                (mode != GameMode.ARMOR && mode != GameMode.MIXED) ||
                (mode == GameMode.MIXED && !mixedModes.contains(GameMode.ARMOR))) {
            return;
        }

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);
        if (entry == null) return;

        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack leggings = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);

        if (this.armorMode == ArmorMode.SET) {
            checkArmorSet(player, entry, helmet, chestplate, leggings, boots);
        } else if (this.armorMode == ArmorMode.PIECE) {
            checkArmorPieces(player, entry, helmet, chestplate, leggings, boots);
        }
    }

    private void checkArmorSet(ServerPlayer player, PlayerEntry entry, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
        String materialName = getArmorMaterialName(helmet);

        if (materialName != "Turtle") {
            // All slots must be filled
            if (helmet.isEmpty() || chestplate.isEmpty() || leggings.isEmpty() || boots.isEmpty()) {
                return;
            }

            // Ensure all armor slots contain armor
            if (!isArmorPiece(helmet, EquipmentSlot.HEAD) ||
                    !isArmorPiece(chestplate, EquipmentSlot.CHEST) ||
                    !isArmorPiece(leggings, EquipmentSlot.LEGS) ||
                    !isArmorPiece(boots, EquipmentSlot.FEET)) {
                return;
            }

            // Check that all pieces match
            if (!materialName.equals(getArmorMaterialName(chestplate)) ||
                    !materialName.equals(getArmorMaterialName(leggings)) ||
                    !materialName.equals(getArmorMaterialName(boots))) {
                return;
            }
        }

        // Already claimed
        if (claimedItems.contains(materialName)) {
            if (snarkyMessages) {
                player.sendSystemMessage(Component.literal("❌ Someone's already worn that one!").withStyle(style -> style.withColor(0xFF5555)));
            }
            return;
        }

        // Claim the armor set
        claimedItems.add(materialName);
        entry.addClaim(materialName, GoalType.ARMOR);

        broadcastToServer(
                player.level().getServer(),
                Component.literal("🛡 " + entry.getName() + " completed " + materialName + " armor set!")
                        .withStyle(style -> style.withColor(entry.getColor()))
        );

        LockoutNetworking.broadcastState(
                player.level().getServer(),
                goal,
                new ArrayList<>(players.values()),
                mode,
                paused,
                pausedPlayerName
        );

        if (entry.getScore() >= goal) {
            win(player, entry);
        }
        else {
            handleSwitch(player);
        }
    }

    private void checkArmorPieces(ServerPlayer player, PlayerEntry entry, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
        // Check each piece individually
        ItemStack[] armorPieces = {helmet, chestplate, leggings, boots};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        for (int i = 0; i < armorPieces.length; i++) {
            ItemStack piece = armorPieces[i];
            EquipmentSlot slot = slots[i];

            // Skip empty or non-armor slots
            if (piece.isEmpty() || !isArmorPiece(piece, slot)) {
                continue;
            }

            String materialName = getArmorMaterialName(piece);

            // Skip if already claimed
            if (claimedItems.contains(materialName)) {
                continue;
            }

            // Claim this armor material
            claimedItems.add(materialName);
            entry.addClaim(materialName, GoalType.ARMOR);

            broadcastToServer(
                    player.level().getServer(),
                    Component.literal("🛡 " + entry.getName() + " got " + materialName + " armor!")
                            .withStyle(style -> style.withColor(entry.getColor()))
            );

            LockoutNetworking.broadcastState(
                    player.level().getServer(),
                    goal,
                    new ArrayList<>(players.values()),
                    mode,
                    paused,
                    pausedPlayerName
            );

            if (entry.getScore() >= goal) {
                win(player, entry);
                return; // Stop checking if they won
            }
        }
    }

    public void handleBreed(ServerPlayer player, Animal animal) {
        if (!active || paused || isCountingDown ||
                (mode != GameMode.BREED && mode != GameMode.MIXED) ||
                (mode == GameMode.MIXED && !mixedModes.contains(GameMode.BREED))) {
            return;
        }

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);
        if (entry == null) return;

        // Get the entity type identifier
        Identifier entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType());
        String animalKey = entityTypeId.toString();

        if (claimedItems.contains(animalKey)) {
            if (snarkyMessages) {
                player.sendSystemMessage(Component.literal("❌ Someone's already bred that one!").withStyle(style -> style.withColor(0xFF5555)));
            }
            return;
        }

        String animalName = Component.translatable(animal.getType().getDescriptionId()).getString();

        claimedItems.add(animalName);
        entry.addClaim(animalName, GoalType.BREED);

        broadcastToServer(player.level().getServer(),
                Component.literal("❤ " + entry.getName() + " bred " + animalName.toLowerCase() + "s!").withStyle(style -> style.withColor(entry.getColor())));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);

        if (entry.getScore() >= goal) {
            win(player, entry);
        } else {
            handleSwitch(player);
        }
    }

    public void handleAdvancement(ServerPlayer player, AdvancementHolder advancement) {
        if (!active || paused || isCountingDown ||
                (mode != GameMode.ADVANCEMENTS && mode != GameMode.MIXED) ||
                (mode == GameMode.MIXED && !mixedModes.contains(GameMode.ADVANCEMENTS))) {
            return;
        }


        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);
        if (entry == null) return;

        String advancementKey = advancement.toString();

        if (claimedItems.contains(advancementKey)) {
            if (snarkyMessages) {
                player.sendSystemMessage(Component.literal("❌ Someone's already claimed that one!").withStyle(style -> style.withColor(0xFF5555)));
            }
            return;
        }

        claimedItems.add(advancementKey);
        entry.addClaim(advancementKey, GoalType.ADVANCEMENT);

        String advancementName = advancement.value().name().map(Component::getString).orElse(advancementKey);

        broadcastToServer(player.level().getServer(),
                Component.literal("🏆 " + entry.getName() + " earned: " + advancementName + "!").withStyle(style -> style.withColor(entry.getColor())));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);

        if (entry.getScore() >= goal) {
            win(player, entry);
        }
        else {
            handleSwitch(player);
        }
    }

    public void handleFood(ServerPlayer player, ItemStack food) {
        if (!active || paused || isCountingDown ||
                (mode != GameMode.FOODS && mode != GameMode.MIXED) ||
                (mode == GameMode.MIXED && !mixedModes.contains(GameMode.FOODS))) {
            return;
        }

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);
        if (entry == null) return;

        String foodName = food.getItem().toString();

        if (claimedItems.contains(foodName)) {
            return; // Already claimed
        }

        claimedItems.add(foodName);
        entry.addClaim(foodName, GoalType.FOOD);

        String displayName = food.getHoverName().getString();

        broadcastToServer(player.level().getServer(),
                Component.literal("🍖 " + entry.getName() + " ate: " + displayName + "!").withStyle(style -> style.withColor(entry.getColor())));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);

        if (entry.getScore() >= goal) {
            win(player, entry);
        }
        else {
            handleSwitch(player);
        }
    }

    private void finishPlayer(ServerPlayer player) {
        player.setGameMode(GameType.SPECTATOR);
    }

    private static void revokeAllAdvancements(ServerPlayer player) {
        PlayerAdvancements advancements = player.getAdvancements();
        MinecraftServer server = player.level().getServer();

        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            AdvancementProgress progress = advancements.getOrStartProgress(holder);

            for (String criterion : progress.getRemainingCriteria()) {
                advancements.revoke(holder, criterion);
            }

            for (String criterion : progress.getCompletedCriteria()) {
                advancements.revoke(holder, criterion);
            }
        }
    }

    private static void revokeAllRecipes(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();

        Collection<RecipeHolder<?>> allRecipes =
                server.getRecipeManager().getRecipes();

        player.resetRecipes(allRecipes);
    }

    private void preparePlayer(ServerPlayer player) {
        player.getInventory().clearContent();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0f);

        revokeAllAdvancements(player);
        revokeAllRecipes(player);

        var server = player.level().getServer();

        if (customSpawnPos != null && customSpawnDimension != null) {
            ServerLevel targetLevel = server.getLevel(customSpawnDimension);
            if (targetLevel != null) {
                player.teleportTo(customSpawnPos.x, customSpawnPos.y, customSpawnPos.z);
            } else {
                teleportToWorldSpawn(player, server);
            }
        } else {
            teleportToWorldSpawn(player, server);
        }

        player.setGameMode(GameType.SURVIVAL);
        player.getActiveEffects().clear();
        player.setExperiencePoints(0);
    }

    private void teleportToWorldSpawn(ServerPlayer player, MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        BlockPos spawnPos = overworld.getLevel().getRespawnData().globalPos().pos();
        player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
    }

    private void freezePlayer(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Integer.MAX_VALUE, 255, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, Integer.MAX_VALUE, 250, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, Integer.MAX_VALUE, 255, false, false));
    }

    private void unfreezePlayer(ServerPlayer player) {
        player.removeEffect(MobEffects.SLOWNESS);
        player.removeEffect(MobEffects.JUMP_BOOST);
        player.removeEffect(MobEffects.MINING_FATIGUE);
    }

    public void handlePause(MinecraftServer server) {
        if (!active || paused) return;

        paused = true;
        broadcastToServer(server, Component.literal("⏸ Game paused").withStyle(style -> style.withColor(0xFFAA00).withBold(true)));

        for (UUID uuid : players.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                freezePlayer(player);
            }
        }

        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void handleUnpause(MinecraftServer server) {
        if (!active || !paused) return;

        paused = false;
        pausedPlayerName = "";

        broadcastToServer(server, Component.literal("▶ Game resumed!").withStyle(style -> style.withColor(0x55FF55).withBold(true)));

        for (UUID uuid : players.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                unfreezePlayer(player);
            }
        }

        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        if (!active || paused) return;

        UUID uuid = player.getUUID();
        if (!players.containsKey(uuid)) return;

        paused = true;
        pausedPlayerName = player.getName().getString();

        MinecraftServer server = player.level().getServer();
        broadcastToServer(server, Component.literal("⏸ Game paused - waiting for " + pausedPlayerName + " to reconnect").withStyle(style -> style.withColor(0xFFAA00).withBold(true)));

        for (UUID otherUuid : players.keySet()) {
            if (!otherUuid.equals(uuid)) {
                ServerPlayer otherPlayer = server.getPlayerList().getPlayer(otherUuid);
                if (otherPlayer != null) {
                    freezePlayer(otherPlayer);
                }
            }
        }

        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void handlePlayerReconnect(ServerPlayer player) {
        if (!active || !paused) return;

        UUID uuid = player.getUUID();
        if (!players.containsKey(uuid)) return;
        if (!player.getName().getString().equals(pausedPlayerName)) return;

        paused = false;
        pausedPlayerName = "";

        MinecraftServer server = player.level().getServer();
        broadcastToServer(server, Component.literal("▶ Game resumed!").withStyle(style -> style.withColor(0x55FF55).withBold(true)));

        for (UUID otherUuid : players.keySet()) {
            ServerPlayer otherPlayer = server.getPlayerList().getPlayer(otherUuid);
            if (otherPlayer != null) {
                unfreezePlayer(otherPlayer);
            }
        }

        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public void setSpawn(ServerPlayer player, Vec3 pos) {
        this.customSpawnPos = pos;
        this.customSpawnDimension = player.level().dimension();
    }

    public String getSpawnInfo() {
        if (customSpawnPos != null && customSpawnDimension != null) {
            String dimensionName = customSpawnDimension.identifier().getPath();
            return String.format("Custom (%.1f, %.1f, %.1f in %s)",
                    customSpawnPos.x, customSpawnPos.y, customSpawnPos.z, dimensionName);
        } else {
            return "World spawn (default)";
        }
    }

    public void stop(MinecraftServer server) {
        if (isCountingDown || paused) {
            for (UUID uuid : players.keySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    unfreezePlayer(player);
                    finishPlayer(player);
                }
            }
        }

        this.active = false;
        this.paused = false;
        this.pausedPlayerName = "";
        this.isCountingDown = false;
        this.countdownTicks = 0;
        this.armorCheckTicks = 0;
        LockoutNetworking.broadcastState(server, goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);
    }

    public boolean isActive() {
        return active;
    }

    public void handleDeath(ServerPlayer player, DamageSource source) {
        if (!active || paused || isCountingDown) return;
        if ((mode != GameMode.DEATH && mode != GameMode.MIXED) || (mode == GameMode.MIXED && !mixedModes.contains(GameMode.DEATH))) {
            if (snarkyMessages) {
                player.sendSystemMessage(Component.literal("❌ That's the wrong game mode!").withStyle(style -> style.withColor(0xFF5555)));
            }
            return;
        }

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);
        if (entry == null) return;

        String uniqueKey;

        if (deathMatchMode == DeathMatchMode.MESSAGE) {
            Component deathMessage = source.getLocalizedDeathMessage(player);
            String rawText = deathMessage.getString();

            uniqueKey = rawText;
            for (PlayerEntry p : players.values()) {
                uniqueKey = uniqueKey.replace(p.getName(), "");
            }
            uniqueKey = uniqueKey.trim();
        } else {
            uniqueKey = source.type().msgId();

            if (source.getEntity() != null) {
                uniqueKey += ":" + source.getEntity().getType().getDescription().getString();
            }
        }

        if (claimedItems.contains(uniqueKey)) {
            if (snarkyMessages) {
                player.sendSystemMessage(Component.literal("❌ Someone's already claimed that one!").withStyle(style -> style.withColor(0xFF5555)));
            }
            return;
        }

        claimedItems.add(uniqueKey);
        entry.addClaim(uniqueKey, GoalType.DEATH);

        Component deathMessage = source.getLocalizedDeathMessage(player);
        broadcastToServer(player.level().getServer(),
                Component.literal("⬛ " + entry.getName() + " got a point! ").withStyle(style -> style.withColor(entry.getColor()))
                        .append(Component.literal("(" + deathMessage.getString() + ")").withStyle(style -> style.withColor(0xAAAAAA))));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);

        if (entry.getScore() >= goal) {
            win(player, entry);
        }
        else {
            handleSwitch(player);
        }
    }

    public String getPlayerWithColor(int color) {
        for (PlayerEntry entry : players.values()) {
            if (entry.getColor() == color) {
                return entry.getName();
            }
        }
        return null;
    }

    public void handleKill(ServerPlayer player, LivingEntity killed) {
        if (!active || paused || isCountingDown ||
                (mode != GameMode.KILLS && mode != GameMode.MIXED) ||
                (mode == GameMode.MIXED && !mixedModes.contains(GameMode.KILLS))) {
            return;
        }

        UUID uuid = player.getUUID();
        PlayerEntry entry = players.get(uuid);
        if (entry == null) return;

        String entityName = killed.getType().getDescription().getString();

        if (claimedItems.contains(entityName)) {
            if (snarkyMessages) {
                player.sendSystemMessage(Component.literal("❌ Someone's already claimed that one!").withStyle(style -> style.withColor(0xFF5555)));
            }
            return;
        }

        claimedItems.add(entityName);
        entry.addClaim(entityName, GoalType.KILL);

        broadcastToServer(player.level().getServer(),
                Component.literal("⚔ " + entry.getName() + " killed a " + entityName + "!").withStyle(style -> style.withColor(entry.getColor())));

        LockoutNetworking.broadcastState(player.level().getServer(), goal, new ArrayList<>(players.values()), mode, paused, pausedPlayerName);

        if (entry.getScore() >= goal) {
            win(player, entry);
        }
        else {
            handleSwitch(player);
        }
    }

    private void win(ServerPlayer player, PlayerEntry winner) {
        broadcastToServer(player.level().getServer(),
                Component.literal("🏆 " + winner.getName() + " WINS! 🏆").withStyle(style -> style.withBold(true).withColor(0x55FF55)));
        stop(player.level().getServer());
    }

    private void broadcastToServer(MinecraftServer server, Component msg) {
        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(msg, false);
        }
    }

    public Map<UUID, PlayerEntry> getPlayers() {
        return players;
    }
}