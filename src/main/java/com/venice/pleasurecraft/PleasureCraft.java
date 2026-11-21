package com.venice.pleasurecraft;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Mod(PleasureCraft.MODID)
public class PleasureCraft {

    public static final String MODID = "pleasurecraft";

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<EntityType<SexNpcEntity>> SEX_NPC = ENTITIES.register("sex_npc",
            () -> EntityType.Builder.of(SexNpcEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.8f).clientTrackingRange(12).build("sex_npc"));

    public static final RegistryObject<Item> FUTANARI_ELIXIR =
            ITEMS.register("futanari_elixir", FutanariElixirItem::new);

    public static final RegistryObject<Item> SEX_NPC_EGG = ITEMS.register("sex_npc_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(SEX_NPC, 0xE8B4A0, 0xC96F7A,
                    new Item.Properties()));

    public PleasureCraft() {
        IEventBus mod = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(mod);
        ITEMS.register(mod);
        mod.addListener(PleasureCraft::attributes);
        mod.addListener(PleasureCraft::tabs);
        MinecraftForge.EVENT_BUS.register(new CommonEvents());
        Net.init();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(new ClientEvents());
            MinecraftForge.EVENT_BUS.register(new PenisRenderer());
            mod.addListener(ClientModBus::layers);
            mod.addListener(ClientModBus::renderers);
        });
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(SEX_NPC.get(), SexNpcEntity.createAttributes().build());
    }

    private static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            e.accept(SEX_NPC_EGG);
            e.accept(FUTANARI_ELIXIR);
        }
    }

    // ==================== ENUMS ====================

    public enum Gender {
        NONE, MALE, FEMALE;

        public static Gender byId(int i) {
            Gender[] v = values();
            return i >= 0 && i < v.length ? v[i] : NONE;
        }
    }

    public enum SceneType {
        DOGGY(Family.STRAIGHT, 0.95, -1.35, 0, 0, 90, -20, 120),
        FACESITTING(Family.STRAIGHT, 0.25, -0.85, 0, 180, 5, -55, 120),
        MISSIONARY(Family.STRAIGHT, 1.10, -1.40, 0, 180, -90, -30, 120),
        BLOWJOB(Family.STRAIGHT, 0.60, -1.25, 0, 180, 20, -25, 100),
        DOGGY_FUTA(Family.FUTA, 0.75, -1.40, 0, 180, 50, -40, 120),
        FACESITTING_FUTA(Family.FUTA, 0.25, -0.85, 0, 180, 5, -55, 120),
        MISSIONARY_FUTA(Family.FUTA, 0.55, -1.25, 0, 180, 70, -55, 120),
        BLOWJOB_FUTA(Family.FUTA, 0.42, -1.25, 0, 180, 18, -22, 110),
        SCISSORS(Family.LESBIAN, 0.85, -1.50, 0, 180, -85, -50, 120),
        CUNNILINGUS_SUB(Family.LESBIAN, 0.50, -1.35, 0, 180, -85, -45, 110),
        CUNNILINGUS_DOM(Family.LESBIAN, 0.45, -1.35, 0, 180, 78, -55, 110);

        public enum Family { STRAIGHT, FUTA, LESBIAN }

        public final Family family;
        public final double fwd, up, right;
        public final float yawOff, bodyPitch, playerPitch;
        public final int duration;

        SceneType(Family family, double fwd, double up, double right,
                  float yawOff, float bodyPitch, float playerPitch, int duration) {
            this.family = family;
            this.fwd = fwd;
            this.up = up;
            this.right = right;
            this.yawOff = yawOff;
            this.bodyPitch = bodyPitch;
            this.playerPitch = playerPitch;
            this.duration = duration;
        }

        public static List<SceneType> forPlayer(Gender g, boolean futa) {
            List<SceneType> out = new ArrayList<>();
            for (SceneType t : values()) {
                if (g == Gender.MALE && !futa && t.family == Family.STRAIGHT) out.add(t);
                if (g == Gender.MALE && futa && t.family == Family.FUTA) out.add(t);
                if (g == Gender.FEMALE && !futa && t.family == Family.LESBIAN) out.add(t);
                if (g == Gender.FEMALE && futa && t.family == Family.FUTA) out.add(t);
            }
            return out;
        }

        public static SceneType byId(int id) {
            SceneType[] v = values();
            return id >= 0 && id < v.length ? v[id] : DOGGY;
        }

        public String label() {
            switch (this) {
                case DOGGY: return "Doggy";
                case FACESITTING: return "Facesitting";
                case MISSIONARY: return "Missionary";
                case BLOWJOB: return "BlowJob";
                case DOGGY_FUTA: return "Doggy (Futa)";
                case FACESITTING_FUTA: return "Facesitting (Futa)";
                case MISSIONARY_FUTA: return "Missionary (Futa)";
                case BLOWJOB_FUTA: return "BlowJob (Futa)";
                case SCISSORS: return "Scissors";
                case CUNNILINGUS_SUB: return "Cunnilingus SUB";
                case CUNNILINGUS_DOM: return "Cunnilingus DOM";
            }
            return name();
        }
    }

    // ==================== MATH ====================

    public static final class SceneMath {
        public static Vec3 npcFeet(Player p, SceneType s) {
            Vec3 eye = p.getEyePosition();
            double yaw = Math.toRadians(p.getYRot());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            return new Vec3(eye.x + fx * s.fwd + rx * s.right,
                    eye.y + s.up,
                    eye.z + fz * s.fwd + rz * s.right);
        }

        public static float npcYaw(Player p, SceneType s) {
            return p.getYRot() + s.yawOff;
        }
    }

    // ==================== CAPABILITY ====================

    public static class PlayerData {
        private Gender gender = Gender.NONE;
        private boolean futanari, inScene;
        public Gender getGender() { return gender; }
        public void setGender(Gender g) { gender = g; }
        public boolean isFutanari() { return futanari; }
        public void setFutanari(boolean b) { futanari = b; }
        public boolean inScene() { return inScene; }
        public void setInScene(boolean b) { inScene = b; }
    }

    public static final class PCap {
        public static final ResourceLocation ID = new ResourceLocation(MODID, "player_data");
        public static final Capability<PlayerData> DATA =
                CapabilityManager.get(new CapabilityToken<PlayerData>() {});

        public static class Provider implements ICapabilitySerializable<CompoundTag> {
            private final PlayerData data = new PlayerData();
            private final LazyOptional<PlayerData> opt = LazyOptional.of(() -> data);

            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                return cap == DATA ? opt.cast() : LazyOptional.empty();
            }

            @Override
            public CompoundTag serializeNBT() {
                CompoundTag t = new CompoundTag();
                t.putInt("gender", data.getGender().ordinal());
                t.putBoolean("futa", data.isFutanari());
                return t;
            }

            @Override
            public void deserializeNBT(CompoundTag t) {
                data.setGender(Gender.byId(t.getInt("gender")));
                data.setFutanari(t.getBoolean("futa"));
            }
        }
    }

    // ==================== ENTITY ====================

    public static class SexNpcEntity extends PathfinderMob {
        public static final EntityDataAccessor<Boolean> FUTA =
                SynchedEntityData.defineId(SexNpcEntity.class, EntityDataSerializers.BOOLEAN);

        public SexNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
            setPersistenceRequired();
        }

        public static AttributeSupplier.Builder createAttributes() {
            return PathfinderMob.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, 40.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.28D);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            entityData.define(FUTA, false);
        }

        public boolean isFutanari() { return entityData.get(FUTA); }

        public void setFutanari(boolean b) {
            entityData.set(FUTA, b);
            if (!level().isClientSide) Net.toTracking(this, new Net.SetFutanari(getId(), b));
        }

        @Override
        protected void registerGoals() {
            goalSelector.addGoal(0, new FloatGoal(this));
            goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8f));
            goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.6D));
            goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        }

        @Override
        public boolean removeWhenFarAway(double d) { return false; }

        @Override
        public InteractionResult mobInteract(Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(FUTANARI_ELIXIR.get())) {
                if (!level().isClientSide) {
                    setFutanari(!isFutanari());
                    gameEvent(GameEvent.ENTITY_INTERACT);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    level().playSound(null, blockPosition(), SoundEvents.BREWING_STAND_BREW,
                            getSoundSource(), 1f, 1.2f);
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
            if (!level().isClientSide && player instanceof ServerPlayer sp && !CommonEvents.isBusy(player)) {
                Net.toClient(sp, new Net.OpenDialogue(getId()));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        @Override
        public void addAdditionalSaveData(CompoundTag t) {
            super.addAdditionalSaveData(t);
            t.putBoolean("futanari", isFutanari());
        }

        @Override
        public void readAdditionalSaveData(CompoundTag t) {
            super.readAdditionalSaveData(t);
            setFutanari(t.getBoolean("futanari"));
        }
    }

    // ==================== ITEM ====================

    public static class FutanariElixirItem extends Item {
        public FutanariElixirItem() {
            super(new Item.Properties().stacksTo(16));
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
            tip.add(Component.literal("ПКМ по НПС - выдать или снять пенис"));
        }
    }

    // ==================== SCENE ====================

    public static final class SceneManager {
        private record Session(ServerPlayer player, SexNpcEntity npc, SceneType scene, int tick) {}

        private static final Map<UUID, Session> SESSIONS = new HashMap<>();

        public static boolean start(ServerPlayer p, SexNpcEntity npc, SceneType s) {
            if (SESSIONS.containsKey(p.getUUID())) return false;
            p.getCapability(PCap.DATA).ifPresent(d -> d.setInScene(true));
            npc.setNoAi(true);
            npc.setNoGravity(true);
            npc.setInvulnerable(true);
            npc.setDeltaMovement(Vec3.ZERO);
            npc.setYRot(SceneMath.npcYaw(p, s));
            npc.yHeadRot = npc.getYRot();
            npc.yBodyRot = npc.getYRot();
            SESSIONS.put(p.getUUID(), new Session(p, npc, s, 0));
            Net.toClient(p, new Net.StartScene(npc.getId(), s.ordinal(), s.duration));
            p.level().playSound(null, npc.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER,
                    npc.getSoundSource(), 1f, 1f);
            return true;
        }

        public static void tick() {
            if (SESSIONS.isEmpty()) return;
            SESSIONS.entrySet().removeIf(entry -> {
                Session s = entry.getValue();
                ServerPlayer p = s.player;
                if (p.isRemoved() || s.npc.isRemoved() || !p.isAlive() || p.level() != s.npc.level()) {
                    finish(s, false);
                    return true;
                }
                Vec3 feet = SceneMath.npcFeet(p, s.scene);
                s.npc.setPos(feet.x, feet.y, feet.z);
                s.npc.setOldPosAndRot();
                float yaw = SceneMath.npcYaw(p, s.scene);
                s.npc.setYRot(yaw);
                s.npc.yHeadRot = yaw;
                s.npc.yBodyRot = yaw;

                int t = s.tick + 1;
                Net.toClient(p, new Net.SceneTick(t, s.scene.duration));
                if (t >= s.scene.duration) {
                    climax(s);
                    finish(s, true);
                    return true;
                }
                entry.setValue(new Session(p, s.npc, s.scene, t));
                return false;
            });
        }

        private static void climax(Session s) {
            ServerLevel lvl = (ServerLevel) s.player.level();
            Vec3 c = s.npc.position().add(0, 1.0, 0);
            for (int i = 0; i < 24; i++) {
                double a = lvl.random.nextDouble() * Math.PI * 2;
                double r = 0.25 + lvl.random.nextDouble() * 0.5;
                lvl.sendParticles(ParticleTypes.HEART, c.x + Math.cos(a) * r,
                        c.y + lvl.random.nextDouble() * 0.6, c.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0.01);
            }
            lvl.playSound(null, s.player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                    s.player.getSoundSource(), 1f, 1.4f);
            Net.toClient(s.player, new Net.Climax());
        }

        private static void finish(Session s, boolean reopen) {
            s.player.getCapability(PCap.DATA).ifPresent(d -> d.setInScene(false));
            s.npc.setNoAi(false);
            s.npc.setNoGravity(false);
            s.npc.setInvulnerable(false);
            Net.toClient(s.player, new Net.EndScene());
            if (reopen && !s.npc.isRemoved()) Net.toClient(s.player, new Net.OpenDialogue(s.npc.getId()));
        }
    }

    // ==================== NETWORK ====================

    public static final class Net {
        private static final String VER = "1";
        public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(MODID, "main"), () -> VER, VER::equals, VER::equals);
        private static int id = 0;

        public static void init() {
            CH.registerMessage(id++, SyncGender.class, SyncGender::enc, SyncGender::dec, SyncGender::handle);
            CH.registerMessage(id++, OpenDialogue.class, OpenDialogue::enc, OpenDialogue::dec, OpenDialogue::handle);
            CH.registerMessage(id++, StartScene.class, StartScene::enc, StartScene::dec, StartScene::handle);
            CH.registerMessage(id++, SceneTick.class, SceneTick::enc, SceneTick::dec, SceneTick::handle);
            CH.registerMessage(id++, EndScene.class, EndScene::enc, EndScene::dec, EndScene::handle);
            CH.registerMessage(id++, Climax.class, Climax::enc, Climax::dec, Climax::handle);
            CH.registerMessage(id++, SetFutanari.class, SetFutanari::enc, SetFutanari::dec, SetFutanari::handle);
            CH.registerMessage(id++, SetGenderC2S.class, SetGenderC2S::enc, SetGenderC2S::dec, SetGenderC2S::handle);
            CH.registerMessage(id++, StartSceneC2S.class, StartSceneC2S::enc, StartSceneC2S::dec, StartSceneC2S::handle);
        }

        public static void toClient(ServerPlayer p, Object msg) {
            CH.send(PacketDistributor.PLAYER.with(() -> p), msg);
        }

        public static void toTracking(Entity e, Object msg) {
            CH.send(PacketDistributor.TRACKING_ENTITY.with(() -> e), msg);
        }

        public static void toServer(Object msg) { CH.sendToServer(msg); }

        private static void client(Runnable r) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> r);
        }

        public record SyncGender(int gender) {
            public static void enc(SyncGender m, FriendlyByteBuf b) { b.writeVarInt(m.gender); }
            public static SyncGender dec(FriendlyByteBuf b) { return new SyncGender(b.readVarInt()); }
            public static void handle(SyncGender m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> client(() -> ClientState.localGender = Gender.byId(m.gender)));
                c.get().setPacketHandled(true);
            }
        }

        public record OpenDialogue(int entityId) {
            public static void enc(OpenDialogue m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); }
            public static OpenDialogue dec(FriendlyByteBuf b) { return new OpenDialogue(b.readVarInt()); }
            public static void handle(OpenDialogue m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> client(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null && mc.level.getEntity(m.entityId) instanceof SexNpcEntity npc) {
                        mc.setScreen(new DialogueScreen(npc));
                    }
                }));
                c.get().setPacketHandled(true);
            }
        }

        public record StartScene(int entityId, int scene, int duration) {
            public static void enc(StartScene m, FriendlyByteBuf b) {
                b.writeVarInt(m.entityId); b.writeVarInt(m.scene); b.writeVarInt(m.duration);
            }
            public static StartScene dec(FriendlyByteBuf b) {
                return new StartScene(b.readVarInt(), b.readVarInt(), b.readVarInt());
            }
            public static void handle(StartScene m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> client(() ->
                        ClientState.start(m.entityId, SceneType.byId(m.scene), m.duration)));
                c.get().setPacketHandled(true);
            }
        }

        public record SceneTick(int tick, int max) {
            public static void enc(SceneTick m, FriendlyByteBuf b) { b.writeVarInt(m.tick); b.writeVarInt(m.max); }
            public static SceneTick dec(FriendlyByteBuf b) { return new SceneTick(b.readVarInt(), b.readVarInt()); }
            public static void handle(SceneTick m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> client(() -> { ClientState.tick = m.tick; ClientState.maxTick = m.max; }));
                c.get().setPacketHandled(true);
            }
        }

        public record EndScene() {
            public static void enc(EndScene m, FriendlyByteBuf b) {}
            public static EndScene dec(FriendlyByteBuf b) { return new EndScene(); }
            public static void handle(EndScene m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> client(ClientState::stop));
                c.get().setPacketHandled(true);
            }
        }

        public record Climax() {
            public static void enc(Climax m, FriendlyByteBuf b) {}
            public static Climax dec(FriendlyByteBuf b) { return new Climax(); }
            public static void handle(Climax m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> client(() -> ClientState.climaxFlash = 20));
                c.get().setPacketHandled(true);
            }
        }

        public record SetFutanari(int entityId, boolean value) {
            public static void enc(SetFutanari m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeBoolean(m.value); }
            public static SetFutanari dec(FriendlyByteBuf b) { return new SetFutanari(b.readVarInt(), b.readBoolean()); }
            public static void handle(SetFutanari m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> client(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null && mc.level.getEntity(m.entityId) instanceof SexNpcEntity npc) {
                        npc.getEntityData().set(SexNpcEntity.FUTA, m.value);
                    }
                }));
                c.get().setPacketHandled(true);
            }
        }

        public record SetGenderC2S(int gender) {
            public static void enc(SetGenderC2S m, FriendlyByteBuf b) { b.writeVarInt(m.gender); }
            public static SetGenderC2S dec(FriendlyByteBuf b) { return new SetGenderC2S(b.readVarInt()); }
            public static void handle(SetGenderC2S m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> {
                    ServerPlayer p = c.get().getSender();
                    if (p != null) p.getCapability(PCap.DATA).ifPresent(d -> d.setGender(Gender.byId(m.gender)));
                });
                c.get().setPacketHandled(true);
            }
        }

        public record StartSceneC2S(int entityId, int scene) {
            public static void enc(StartSceneC2S m, FriendlyByteBuf b) { b.writeVarInt(m.entityId); b.writeVarInt(m.scene); }
            public static StartSceneC2S dec(FriendlyByteBuf b) { return new StartSceneC2S(b.readVarInt(), b.readVarInt()); }
            public static void handle(StartSceneC2S m, Supplier<net.minecraftforge.network.NetworkEvent.Context> c) {
                c.get().enqueueWork(() -> {
                    ServerPlayer p = c.get().getSender();
                    if (p != null && p.level().getEntity(m.entityId) instanceof SexNpcEntity npc) {
                        SceneManager.start(p, npc, SceneType.byId(m.scene));
                    }
                });
                c.get().setPacketHandled(true);
            }
        }
    }

    // ==================== COMMON EVENTS ====================

    public static final class CommonEvents {
        @SubscribeEvent
        public void attach(AttachCapabilitiesEvent<Entity> e) {
            if (e.getObject() instanceof Player) e.addCapability(PCap.ID, new PCap.Provider());
        }

        @SubscribeEvent
        public void clone(PlayerEvent.Clone e) {
            if (!e.isWasDeath()) return;
            e.getOriginal().getCapability(PCap.DATA).ifPresent(o ->
                    e.getEntity().getCapability(PCap.DATA).ifPresent(n -> {
                        n.setGender(o.getGender());
                        n.setFutanari(o.isFutanari());
                    }));
        }

        @SubscribeEvent
        public void login(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) {
                sp.getCapability(PCap.DATA).ifPresent(d -> Net.toClient(sp, new Net.SyncGender(d.getGender().ordinal())));
            }
        }

        @SubscribeEvent
        public void tracking(PlayerEvent.StartTracking e) {
            if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer sp) {
                t.getCapability(PCap.DATA).ifPresent(d -> Net.toClient(sp, new Net.SyncGender(d.getGender().ordinal())));
            }
        }

        @SubscribeEvent
        public void serverTick(TickEvent.ServerTickEvent e) {
            if (e.phase == TickEvent.Phase.END) SceneManager.tick();
        }

        public static boolean isBusy(Player p) {
            return p.getCapability(PCap.DATA).map(PlayerData::inScene).orElse(false);
        }
    }

    // ==================== CLIENT ====================

    public static final class ClientState {
        public static Gender localGender = Gender.NONE;
        public static boolean active;
        public static int entityId = -1;
        public static SceneType scene = SceneType.DOGGY;
        public static int tick, maxTick = 120, climaxFlash;
        public static float partialTick, lockedYaw, lockedPitch;

        public static void start(int id, SceneType t, int dur) {
            Minecraft mc = Minecraft.getInstance();
            active = true; entityId = id; scene = t; tick = 0; maxTick = dur;
            if (mc.player != null) {
                lockedYaw = Math.round(mc.player.getYRot() / 45f) * 45f;
                lockedPitch = t.playerPitch;
            }
            if (mc.screen != null) mc.setScreen(null);
        }

        public static void stop() { active = false; entityId = -1; tick = 0; }

        public static boolean isActive(int id) { return active && id == entityId; }
        public static boolean isActive() { return active; }
    }

    public static final class SkinTex {
        public static final ResourceLocation TEX = new ResourceLocation(MODID, "generated/skin");
        private static boolean built;

        public static ResourceLocation get() {
            if (!built) build();
            return TEX;
        }

        private static void px(NativeImage i, int x, int y, int r, int g, int b, int a) {
            if (x < 0 || y < 0 || x >= i.getWidth() || y >= i.getHeight()) return;
            i.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
        }

        private static void rect(NativeImage i, int x0, int y0, int w, int h, int r, int g, int b, int a) {
            for (int x = x0; x < x0 + w; x++)
                for (int y = y0; y < y0 + h; y++) px(i, x, y, r, g, b, a);
        }

        private static void build() {
            built = true;
            NativeImage i = new NativeImage(128, 128, true);
            rect(i, 0, 0, 128, 128, 232, 186, 162, 255);
            rect(i, 53, 70, 2, 2, 60, 40, 35, 255);
            rect(i, 57, 70, 2, 2, 60, 40, 35, 255);
            rect(i, 53, 69, 2, 1, 90, 60, 50, 255);
            rect(i, 57, 69, 2, 1, 90, 60, 50, 255);
            rect(i, 55, 73, 2, 1, 200, 110, 110, 255);
            rect(i, 0, 64, 10, 10, 226, 130, 138, 255);
            rect(i, 1, 65, 2, 2, 176, 74, 88, 255);
            rect(i, 16, 64, 12, 12, 226, 130, 138, 255);
            rect(i, 17, 65, 3, 2, 150, 62, 78, 255);
            rect(i, 18, 65, 1, 2, 96, 40, 52, 255);
            rect(i, 24, 64, 12, 12, 214, 140, 120, 255);
            rect(i, 25, 65, 2, 2, 130, 84, 70, 255);
            rect(i, 48, 0, 10, 10, 232, 186, 162, 255);
            rect(i, 60, 0, 12, 12, 232, 168, 158, 255);
            rect(i, 61, 1, 8, 8, 224, 130, 132, 255);
            rect(i, 63, 3, 4, 4, 190, 92, 96, 255);
            Minecraft.getInstance().getTextureManager().register(TEX, new DynamicTexture(i, true));
        }
    }

    public static final class NpcModel extends EntityModel<SexNpcEntity> {
        public static final ModelLayerLocation LAYER =
                new ModelLayerLocation(new ResourceLocation(MODID, "sex_npc"), "main");

        private final ModelPart root, hips, body, head, armL, armR, legL, legR;
        private final ModelPart breastL, breastR, buttL, buttR, vagina, anus, penisRoot, shaft;

        public NpcModel(ModelPart root) {
            this.root = root;
            this.hips = root.getChild("hips");
            this.body = hips.getChild("body");
            this.head = body.getChild("head");
            this.armL = body.getChild("arm_l");
            this.armR = body.getChild("arm_r");
            this.legL = hips.getChild("leg_l");
            this.legR = hips.getChild("leg_r");
            this.breastL = body.getChild("breast_l");
            this.breastR = body.getChild("breast_r");
            this.buttL = hips.getChild("butt_l");
            this.buttR = hips.getChild("butt_r");
            this.vagina = hips.getChild("vagina");
            this.anus = hips.getChild("anus");
            this.penisRoot = hips.getChild("penis");
            this.shaft = penisRoot.getChild("shaft");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();

            PartDefinition hips = root.addOrReplaceChild("hips",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-4f, -1f, -2f, 8f, 5f, 4f),
                    PartPose.offset(0f, 10f, 0f));

            PartDefinition body = hips.addOrReplaceChild("body",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-4f, -10f, -2f, 8f, 10f, 4f),
                    PartPose.ZERO);

            body.addOrReplaceChild("head",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-4f, -8f, -4f, 8f, 8f, 8f),
                    PartPose.offset(0f, -10f, 0f));

            body.addOrReplaceChild("arm_r",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-1f, 0f, -1f, 2f, 10f, 2f),
                    PartPose.offset(-5f, -9f, 0f));
            body.addOrReplaceChild("arm_l",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-1f, 0f, -1f, 2f, 10f, 2f),
                    PartPose.offset(5f, -9f, 0f));

            hips.addOrReplaceChild("leg_r",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-1.5f, 0f, -1.5f, 3f, 10f, 3f),
                    PartPose.offset(-2f, 4f, 0f));
            hips.addOrReplaceChild("leg_l",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-1.5f, 0f, -1.5f, 3f, 10f, 3f),
                    PartPose.offset(2f, 4f, 0f));

            body.addOrReplaceChild("breast_r",
                    CubeListBuilder.create()
                            .texOffs(48, 64).addBox(-1.7f, -0.4f, -2.6f, 3.4f, 3.4f, 2.6f)
                            .texOffs(48, 64).addBox(-1.2f, -0.9f, -3.2f, 2.4f, 2.4f, 0.8f),
                    PartPose.offset(-1.9f, -7f, -2f)).addOrReplaceChild("nipple",
                    CubeListBuilder.create().texOffs(0, 64).addBox(-0.6f, -0.6f, -0.5f, 1.2f, 1.2f, 0.5f),
                    PartPose.offset(0f, 1.2f, -3.2f));

            body.addOrReplaceChild("breast_l",
                    CubeListBuilder.create()
                            .texOffs(48, 64).addBox(-1.7f, -0.4f, -2.6f, 3.4f, 3.4f, 2.6f)
                            .texOffs(48, 64).addBox(-1.2f, -0.9f, -3.2f, 2.4f, 2.4f, 0.8f),
                    PartPose.offset(1.9f, -7f, -2f)).addOrReplaceChild("nipple",
                    CubeListBuilder.create().texOffs(0, 64).addBox(-0.6f, -0.6f, -0.5f, 1.2f, 1.2f, 0.5f),
                    PartPose.offset(0f, 1.2f, -3.2f));

            hips.addOrReplaceChild("butt_r",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-2.2f, -2.4f, 0f, 4.4f, 4.4f, 2.4f),
                    PartPose.offset(-1.9f, 2.5f, 2f));
            hips.addOrReplaceChild("butt_l",
                    CubeListBuilder.create().texOffs(48, 64).addBox(-2.2f, -2.4f, 0f, 4.4f, 4.4f, 2.4f),
                    PartPose.offset(1.9f, 2.5f, 2f));

            hips.addOrReplaceChild("vagina",
                    CubeListBuilder.create().texOffs(16, 64).addBox(-1.5f, -1f, -1f, 3f, 2f, 1f),
                    PartPose.offset(0f, 2.6f, -1.9f));

            hips.addOrReplaceChild("anus",
                    CubeListBuilder.create().texOffs(24, 64).addBox(-1f, -1f, 0f, 2f, 2f, 1f),
                    PartPose.offset(0f, 2.6f, 2.1f));

            PartDefinition penis = hips.addOrReplaceChild("penis",
                    CubeListBuilder.create(), PartPose.offset(0f, 2.9f, -2.1f));
            PartDefinition shaft = penis.addOrReplaceChild("shaft",
                    CubeListBuilder.create().texOffs(48, 0).addBox(-0.9f, -0.9f, -6f, 1.8f, 1.8f, 6f),
                    PartPose.rotation(-0.30f, 0f, 0f));
            shaft.addOrReplaceChild("glans",
                    CubeListBuilder.create().texOffs(60, 0).addBox(-1.1f, -1.1f, -2f, 2.2f, 2.2f, 2f),
                    PartPose.ZERO);
            penis.addOrReplaceChild("balls",
                    CubeListBuilder.create().texOffs(48, 0).addBox(-1.6f, 0.5f, -1.6f, 3.2f, 2f, 1.6f),
                    PartPose.ZERO);

            return LayerDefinition.create(mesh, 128, 128);
        }

        @Override
        public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int ov,
                                   float r, float g, float b, float a) {
            root.render(ps, vc, light, ov, r, g, b, a);
        }

        private void reset() {
            hips.setRotation(0f, 0f, 0f);
            body.setRotation(0f, 0f, 0f);
            head.setRotation(0f, 0f, 0f);
            armL.setRotation(0f, 0f, 0.06f);
            armR.setRotation(0f, 0f, -0.06f);
            legL.setRotation(0f, 0f, 0.02f);
            legR.setRotation(0f, 0f, -0.02f);
            breastL.setRotation(0f, 0f, 0f);
            breastR.setRotation(0f, 0f, 0f);
            penisRoot.setRotation(0f, 0f, 0f);
            shaft.setRotation(-0.30f, 0f, 0f);
        }

        @Override
        public void setupAnim(SexNpcEntity e, float limbSwing, float amount, float age,
                              float netHeadYaw, float headPitch) {
            reset();
            penisRoot.visible = e.isFutanari();

            if (!ClientState.isActive(e.getId())) {
                float s = Mth.sin(age * 0.09f) * 0.03f;
                armL.zRot = 0.06f + s;
                armR.zRot = -0.06f - s;
                breastL.xRot = Mth.sin(age * 0.07f) * 0.04f;
                breastR.xRot = -breastL.xRot;
                head.yRot = netHeadYaw * 0.0175f;
                head.xRot = headPitch * 0.0175f;
                return;
            }

            SceneType sc = ClientState.scene;
            float t = ClientState.tick + ClientState.partialTick;
            float p = Mth.clamp(t / (float) ClientState.maxTick, 0f, 1f);
            float speed = sc.duration <= 110 ? 0.55f : 0.42f;
            float thrust = Mth.sin(t * speed);
            float pitchRad = (float) Math.toRadians(sc.bodyPitch);

            hips.xRot = pitchRad;
            head.xRot = (float) Math.toRadians(-sc.bodyPitch * 0.55f);
            head.yRot = 0f;

            switch (sc) {
                case DOGGY -> {
                    float k = thrust * 0.16f;
                    hips.xRot = pitchRad - 0.14f + k;
                    body.xRot = -0.18f - k * 0.5f;
                    head.xRot = -0.55f;
                    armL.xRot = 1.45f; armR.xRot = 1.45f;
                    armL.zRot = 0.10f; armR.zRot = -0.10f;
                    legL.xRot = 0.20f; legR.xRot = 0.20f;
                    legL.zRot = 0.22f; legR.zRot = -0.22f;
                    breastL.xRot = 0.55f + thrust * 0.10f;
                    breastR.xRot = breastL.xRot;
                    penisRoot.xRot = -1.35f;
                }
                case FACESITTING -> {
                    hips.xRot = 0.10f + thrust * 0.05f;
                    body.xRot = -0.10f;
                    head.xRot = 0.35f;
                    armL.xRot = 0.55f; armR.xRot = 0.55f;
                    armL.zRot = 0.55f; armR.zRot = -0.55f;
                    legL.xRot = 1.45f; legR.xRot = 1.45f;
                    legL.zRot = 0.55f; legR.zRot = -0.55f;
                    breastL.xRot = 0.12f; breastR.xRot = 0.12f;
                    penisRoot.xRot = -0.75f;
                }
                case MISSIONARY -> {
                    hips.xRot = pitchRad + 0.10f - thrust * 0.10f;
                    body.xRot = 0.12f;
                    head.xRot = -0.30f;
                    armL.xRot = -0.35f; armR.xRot = -0.35f;
                    armL.zRot = 0.60f; armR.zRot = -0.60f;
                    legL.xRot = 0.85f; legR.xRot = 0.85f;
                    legL.zRot = 0.40f; legR.zRot = -0.40f;
                    breastL.xRot = 0.25f; breastR.xRot = 0.25f;
                    penisRoot.xRot = -1.05f;
                }
                case BLOWJOB -> {
                    body.xRot = 0.18f + thrust * 0.06f;
                    head.xRot = -0.25f + thrust * 0.22f;
                    armL.xRot = 0.95f; armR.xRot = 0.95f;
                    armL.zRot = 0.45f; armR.zRot = -0.45f;
                    legL.xRot = 1.45f; legR.xRot = 1.45f;
                    legL.zRot = 0.10f; legR.zRot = -0.10f;
                    breastL.xRot = 0.45f; breastR.xRot = 0.45f;
                }
                case DOGGY_FUTA -> {
                    hips.xRot = pitchRad - thrust * 0.13f;
                    body.xRot = 0.10f;
                    head.xRot = -0.20f;
                    armL.xRot = 0.90f; armR.xRot = 0.90f;
                    armL.zRot = 0.25f; armR.zRot = -0.25f;
                    legL.xRot = 0.15f; legR.xRot = 0.15f;
                    legL.zRot = 0.16f; legR.zRot = -0.16f;
                    breastL.xRot = 0.60f + thrust * 0.12f;
                    breastR.xRot = breastL.xRot;
                    penisRoot.xRot = -0.55f;
                }
                case FACESITTING_FUTA -> {
                    hips.xRot = 0.10f + thrust * 0.05f;
                    body.xRot = -0.10f;
                    head.xRot = 0.35f;
                    armL.xRot = 0.55f; armR.xRot = 0.55f;
                    armL.zRot = 0.55f; armR.zRot = -0.55f;
                    legL.xRot = 1.45f; legR.xRot = 1.45f;
                    legL.zRot = 0.55f; legR.zRot = -0.55f;
                    penisRoot.xRot = -1.55f;
                }
                case MISSIONARY_FUTA -> {
                    hips.xRot = pitchRad - thrust * 0.12f;
                    body.xRot = 0.05f;
                    head.xRot = -0.30f;
                    armL.xRot = 1.35f; armR.xRot = 1.35f;
                    armL.zRot = 0.15f; armR.zRot = -0.15f;
                    legL.xRot = 0.35f; legR.xRot = 0.35f;
                    legL.zRot = 0.20f; legR.zRot = -0.20f;
                    breastL.xRot = 0.70f; breastR.xRot = 0.70f;
                    penisRoot.xRot = -0.95f;
                }
                case BLOWJOB_FUTA -> {
                    hips.xRot = pitchRad - 0.10f + thrust * 0.05f;
                    body.xRot = 0.10f;
                    head.xRot = -0.35f;
                    armL.xRot = 1.20f; armR.xRot = 1.20f;
                    armL.zRot = 0.10f; armR.zRot = -0.10f;
                    legL.xRot = 1.50f; legR.xRot = 1.50f;
                    breastL.xRot = 0.30f; breastR.xRot = 0.30f;
                    penisRoot.xRot = -1.20f;
                }
                case SCISSORS -> {
                    hips.xRot = pitchRad + 0.08f + thrust * 0.08f;
                    body.xRot = 0.10f;
                    head.xRot = -0.15f;
                    armL.xRot = -0.55f; armR.xRot = -0.55f;
                    armL.zRot = 0.30f; armR.zRot = -0.30f;
                    legL.xRot = 1.35f + thrust * 0.10f; legR.xRot = 1.10f - thrust * 0.10f;
                    legL.zRot = 0.35f; legR.zRot = -0.35f;
                    breastL.xRot = 0.20f; breastR.xRot = 0.20f;
                }
                case CUNNILINGUS_SUB -> {
                    hips.xRot = pitchRad + 0.05f + thrust * 0.06f;
                    body.xRot = 0.08f;
                    head.xRot = 0.10f;
                    armL.xRot = -0.40f; armR.xRot = -0.40f;
                    armL.zRot = 0.45f; armR.zRot = -0.45f;
                    legL.xRot = 1.55f; legR.xRot = 1.55f;
                    legL.zRot = 0.55f; legR.zRot = -0.55f;
                    breastL.xRot = 0.18f; breastR.xRot = 0.18f;
                }
                case CUNNILINGUS_DOM -> {
                    hips.xRot = pitchRad - thrust * 0.10f;
                    body.xRot = 0.15f;
                    head.xRot = -0.55f + thrust * 0.18f;
                    armL.xRot = 1.55f; armR.xRot = 1.55f;
                    armL.zRot = 0.22f; armR.zRot = -0.22f;
                    legL.xRot = 1.50f; legR.xRot = 1.50f;
                    breastL.xRot = 0.75f; breastR.xRot = 0.75f;
                }
            }

            if (p > 0.85f) {
                float shake = Mth.sin(t * 2.4f) * 0.05f * ((p - 0.85f) / 0.15f);
                hips.zRot = shake;
                head.zRot = shake * 0.6f;
            }
        }
    }

    public static final class NpcRenderer extends MobRenderer<SexNpcEntity, NpcModel> {
        public NpcRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new NpcModel(ctx.bakeLayer(NpcModel.LAYER)), 0.42f);
        }

        @Override
        public ResourceLocation getTextureLocation(SexNpcEntity e) { return SkinTex.get(); }

        @Override
        public void render(SexNpcEntity entity, float yaw, float pt, PoseStack ps,
                           MultiBufferSource buf, int light) {
            if (ClientState.isActive(entity.getId()) && Minecraft.getInstance().player != null) {
                Player p = Minecraft.getInstance().player;
                Vec3 feet = SceneMath.npcFeet(p, ClientState.scene);
                entity.setPos(feet.x, feet.y, feet.z);
                entity.xo = feet.x; entity.yo = feet.y; entity.zo = feet.z;
                float y = SceneMath.npcYaw(p, ClientState.scene);
                entity.setYRot(y); entity.yRotO = y;
                entity.yHeadRot = y; entity.yHeadRotO = y;
                entity.yBodyRot = y; entity.yBodyRotO = y;
                yaw = 0f;
                shadowRadius = 0f;
            } else {
                shadowRadius = 0.42f;
            }
            super.render(entity, yaw, pt, ps, buf, light);
        }
    }

    public static final class PenisRenderer {
        private static ModelPart model;

        private static ModelPart model() {
            if (model == null) {
                MeshDefinition mesh = new MeshDefinition();
                PartDefinition root = mesh.getRoot();
                root.addOrReplaceChild("shaft",
                        CubeListBuilder.create().texOffs(48, 0).addBox(-1f, -1f, -7f, 2f, 2f, 7f), PartPose.ZERO);
                root.addOrReplaceChild("glans",
                        CubeListBuilder.create().texOffs(60, 0).addBox(-1.2f, -1.2f, -2.2f, 2.4f, 2.4f, 2.2f),
                        PartPose.offset(0f, 0f, -7f));
                model = LayerDefinition.create(mesh, 128, 128).bakeRoot();
            }
            return model;
        }

        @SubscribeEvent
        public void onStage(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            if (!ClientState.isActive()) return;
            if (ClientState.localGender != Gender.MALE) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(0.11, -0.42, 0.62);
            ps.mulPose(Axis.XP.rotationDegrees(58f));
            ps.scale(0.075f, 0.075f, 0.075f);
            MultiBufferSource.BufferSource b = mc.renderBuffers().bufferSource();
            model().render(ps, b.getBuffer(RenderType.entitySolid(SkinTex.get())),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
            b.endBatch();
            ps.popPose();
        }
    }

    public static final class ClientEvents {
        @SubscribeEvent
        public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (ClientState.climaxFlash > 0) ClientState.climaxFlash--;
            if (ClientState.isActive()) {
                Player p = mc.player;
                p.setYRot(ClientState.lockedYaw); p.yRotO = ClientState.lockedYaw;
                p.setXRot(ClientState.lockedPitch); p.xRotO = ClientState.lockedPitch;
                p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
                p.zza = 0; p.xxa = 0;
                ClientState.partialTick = mc.getFrameTime();
            }
        }

        @SubscribeEvent
        public void join(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) {
            if (ClientState.localGender == Gender.NONE) {
                Minecraft.getInstance().setScreen(new GenderScreen());
            }
        }

        @SubscribeEvent
        public void camera(ViewportEvent.ComputeCameraAngles e) {
            if (ClientState.isActive()) {
                e.setYaw(ClientState.lockedYaw);
                e.setPitch(ClientState.lockedPitch);
            }
        }
    }

    public static final class ClientModBus {
        public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
            e.registerLayerDefinition(NpcModel.LAYER, NpcModel::createBodyLayer);
        }

        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(SEX_NPC.get(), NpcRenderer::new);
        }
    }

    public static class GenderScreen extends Screen {
        public GenderScreen() { super(Component.literal("Выбор пола")); }

        @Override
        protected void init() {
            int cx = width / 2, cy = height / 2;
            addRenderableWidget(Button.builder(Component.literal("Мужской"), b -> pick(Gender.MALE))
                    .bounds(cx - 105, cy - 10, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Женский"), b -> pick(Gender.FEMALE))
                    .bounds(cx + 5, cy - 10, 100, 20).build());
        }

        private void pick(Gender g) {
            ClientState.localGender = g;
            Net.toServer(new Net.SetGenderC2S(g.ordinal()));
            onClose();
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g);
            g.drawCenteredString(font, title, width / 2, height / 2 - 40, 0xFFFFFF);
            g.drawCenteredString(font, "Кем ты хочешь быть в этом мире?", width / 2, height / 2 - 26, 0xAAAAAA);
            super.render(g, mx, my, pt);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }

    public static class DialogueScreen extends Screen {
        private final SexNpcEntity npc;

        public DialogueScreen(SexNpcEntity npc) {
            super(Component.literal("Разговор"));
            this.npc = npc;
        }

        @Override
        protected void init() {
            List<SceneType> list = SceneType.forPlayer(ClientState.localGender, npc.isFutanari());
            int cx = width / 2;
            int y = height / 2 - (list.size() * 24) / 2;
            for (SceneType sc : list) {
                addRenderableWidget(Button.builder(Component.literal(sc.label()), b -> {
                    Net.toServer(new Net.StartSceneC2S(npc.getId(), sc.ordinal()));
                    onClose();
                }).bounds(cx - 110, y, 220, 20).build());
                y += 24;
            }
            addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
                    .bounds(cx - 50, y + 6, 100, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            renderBackground(g);
            g.drawCenteredString(font, title, width / 2, height / 2 - 70, 0xFFFFFF);
            g.drawCenteredString(font, npc.isFutanari() ? "НПС: футанари" : "НПС",
                    width / 2, height / 2 - 56, 0xAAAAAA);
            super.render(g, mx, my, pt);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
