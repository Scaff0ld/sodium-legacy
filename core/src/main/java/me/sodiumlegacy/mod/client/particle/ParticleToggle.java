package me.sodiumlegacy.mod.client.particle;

/**
 * Every particle that can be switched on/off individually from the Advanced page, in the spirit
 * of OptiFine's per-particle settings.
 *
 * <p>Each toggle owns the numeric 1.8.9 particle ids it controls (the ids of
 * {@code EnumParticleTypes}, which is what {@code ParticleManager#addParticle(int, ...)} receives).
 * Toggles without ids ({@link #BLOCK_BREAK}, {@link #BLOCK_BREAKING}) and {@link #FIREWORK} are
 * additionally handled through dedicated hooks, since vanilla spawns those particles without
 * going through the id-based entry point.</p>
 *
 * <p>The {@code key} is what gets persisted in {@code sodiumlegacy-options.json}, so never rename
 * one without a migration.</p>
 */
public enum ParticleToggle {
    // --- World ---
    RAIN_SPLASH("rain_splash", Group.WORLD, 39),
    WATER_SPLASH("water_splash", Group.WORLD, 5, 6),
    BUBBLE("bubble", Group.WORLD, 4),
    SUSPENDED("suspended", Group.WORLD, 7, 8),
    SMOKE("smoke", Group.WORLD, 11, 12),
    FLAME("flame", Group.WORLD, 26),
    LAVA("lava", Group.WORLD, 27),
    DRIP_WATER("drip_water", Group.WORLD, 18),
    DRIP_LAVA("drip_lava", Group.WORLD, 19),
    CLOUD("cloud", Group.WORLD, 29),
    RUNNING("running", Group.WORLD, 37),

    // --- Blocks & items ---
    BLOCK_BREAK("block_break", Group.BLOCKS),
    BLOCK_BREAKING("block_breaking", Group.BLOCKS),
    BLOCK_DUST("block_dust", Group.BLOCKS, 38),
    ITEM_CRACK("item_crack", Group.BLOCKS, 36),
    REDSTONE("redstone", Group.BLOCKS, 30),
    PORTAL("portal", Group.BLOCKS, 24),
    ENCHANT("enchant", Group.BLOCKS, 25),
    NOTE("note", Group.BLOCKS, 23),
    BARRIER("barrier", Group.BLOCKS, 35),

    // --- Entities & combat ---
    CRIT("crit", Group.ENTITIES, 9, 10),
    POTION("potion", Group.ENTITIES, 13, 14, 15, 16, 17),
    EXPLOSION("explosion", Group.ENTITIES, 0, 1, 2),
    FIREWORK("firework", Group.ENTITIES, 3),
    HEART("heart", Group.ENTITIES, 34),
    VILLAGER("villager", Group.ENTITIES, 20, 21, 22),
    SLIME("slime", Group.ENTITIES, 33),
    SNOW("snow", Group.ENTITIES, 31, 32),
    MOB_APPEARANCE("mob_appearance", Group.ENTITIES, 41);

    public enum Group {
        WORLD,
        BLOCKS,
        ENTITIES
    }

    private final String key;
    private final Group group;
    private final int[] ids;

    ParticleToggle(String key, Group group, int... ids) {
        this.key = key;
        this.group = group;
        this.ids = ids;
    }

    /** Persistent identifier used in the config file. */
    public String getKey() {
        return this.key;
    }

    public Group getGroup() {
        return this.group;
    }

    public int[] getIds() {
        return this.ids;
    }

    public String getNameTranslationKey() {
        return "sodiumlegacy.options.particles." + this.key + ".name";
    }
}
