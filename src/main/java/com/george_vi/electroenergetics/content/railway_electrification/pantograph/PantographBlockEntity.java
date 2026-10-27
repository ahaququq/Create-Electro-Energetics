package com.george_vi.electroenergetics.content.railway_electrification.pantograph;

import com.george_vi.electroenergetics.CEETags;
import com.george_vi.electroenergetics.CreateElectroEnergetics;
import com.george_vi.electroenergetics.client.WireRenderer;
import com.george_vi.electroenergetics.config.CEEConfigs;
import com.george_vi.electroenergetics.content.railway_electrification.catenary.CatenaryConnection;
import com.george_vi.electroenergetics.foundation.CEELang;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.infrastructure.ConnectionEntry;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireSimulationState;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class PantographBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, Clearable, Container {
    private static final Logger log = LoggerFactory.getLogger(PantographBlockEntity.class);
    public double power = 0;
    public SpringMovementState springState = SpringMovementState.NONE;
    public Vec3 previousSublevelVelocity = Vec3.ZERO;
    private final ArrayList<ItemStack> inventory = new ArrayList<>();

//    public boolean ownsItem = true;
//    public boolean recessive = false;

    final public PantographPhysicsController physicsController = new PantographPhysicsController(
            level, getBlockState().getValue(PantographBlock.DOUBLE),
            getBlockState().getValue(PantographBlock.FACING),
            worldPosition, null, true
    );

//    public boolean extended = true;
    public DyeColor color = DyeColor.WHITE;

    AttachedNode attachedNode;
    WireSimulationState.WireCutHandle handle;
    InWorldNodeConnection lastConnection = null;

    public PantographBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {

    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (physicsController == null) return false;

        CEELang
                .translate("gui.goggles.pantograph_info")
                .forGoggles(tooltip);

        CEELang
                .translate("gui.goggles.pantograph.control_mode")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        PantographControlMode controlMode = getBlockState().getValue(PantographBlock.CONTROL_MODE);
        LangBuilder controlModeText = CEELang
                .translate("pantograph.control_mode." + controlMode.getTranslationName())
                .style(ChatFormatting.AQUA);
        if (controlMode.shouldObfuscate()) controlModeText = controlModeText.style(ChatFormatting.OBFUSCATED);
        controlModeText
                .forGoggles(tooltip, 1);

        CEELang
                .translate("gui.goggles.pantograph.spring_position")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang
                .number(Mth.map(
                        physicsController.springPosition,
                        physicsController.getLowerLimit(),
                        physicsController.getUpperLimit(),
                        0,
                        100
                        ))
                .text("%")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        CEELang
                .translate("gui.goggles.pantograph.arm_position")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang
                .number(Mth.map(
                        physicsController.length,
                        physicsController.getLowerLimit(),
                        physicsController.getUpperLimit(),
                        0,
                        100
                        ))
                .text("%")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        if (physicsController.distanceFromWire() != Float.POSITIVE_INFINITY) {
            CEELang
                    .translate("gui.goggles.pantograph.wire_distance")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);

            CreateLang
                    .number(physicsController.distanceFromWire())
                    .add(CEELang.translate("generic.meter"))
                    .style(ChatFormatting.AQUA)
                    .forGoggles(tooltip, 1);
        }

		if (!controlMode.isMotorized() || springState == SpringMovementState.NONE) return true;

//            CEELang
//                    .translate("gui.goggles.pantograph.control_stats")
//                    .style(ChatFormatting.GRAY)
//                    .forGoggles(tooltip);

		CEELang
				.translate("gui.goggles.energy_consumption")
				.style(ChatFormatting.GRAY)
				.forGoggles(tooltip);

		CEELang.builder()
				.add(CEELang.formatPower(power))
				.style(ChatFormatting.AQUA)
				.space()
				.add(CEELang
						.translate("gui.goggles.while_" + springState.getSerializedName())
						.style(ChatFormatting.DARK_GRAY))
				.forGoggles(tooltip, 1);

		return true;
    }

    @Override
    public void tick() {
        super.tick();

//        if (physicsController == null) {
//            physicsController =
//        }

        if (getBlockState().getValue(PantographBlock.CONTROL_MODE).needsUpdating()) {
            becomeDouble();
            updateControlMode();
        }

        physicsController.level = level;
        physicsController.isDouble = getBlockState().getValue(PantographBlock.DOUBLE);
        physicsController.facing = getBlockState().getValue(PantographBlock.FACING);
        physicsController.localPos = worldPosition;

        level.getProfiler().push("Pantograph.tick");

        SubLevelAccess subLevelAccess = SableCompanion.INSTANCE.getContaining(level, worldPosition);
        Vec3 velocity = SableCompanion.INSTANCE.getVelocity(level, (Position) worldPosition.getCenter());
        Vec3 acceleration = previousSublevelVelocity.subtract(velocity);
        previousSublevelVelocity = velocity;
        float gravityCoefficient = -1;

        if (subLevelAccess == null) {
            if (level.isClientSide)
                handleOnClient(p -> p, p -> p, Vec3.ZERO, gravityCoefficient);
            else
                handleOnServer(p -> p, p -> p, Vec3.ZERO, gravityCoefficient);
        } else {
            gravityCoefficient = (float) subLevelAccess.logicalPose().transformNormalInverse(new Vec3(0, -1, 0)).y;
            if (level.isClientSide)
                handleOnClient(
                        subLevelAccess.logicalPose()::transformPosition,
                        subLevelAccess.logicalPose()::transformPositionInverse,
                        subLevelAccess.logicalPose().transformNormalInverse(acceleration),
                        gravityCoefficient
                );
            else
                handleOnServer(
                        subLevelAccess.logicalPose()::transformPosition,
                        subLevelAccess.logicalPose()::transformPositionInverse,
                        subLevelAccess.logicalPose().transformNormalInverse(acceleration),
                        gravityCoefficient
                );
        }

        level.getProfiler().pop();
        return;
    }

	private void becomeDouble() {
        BlockEntity other = level.getBlockEntity(worldPosition.relative(getBlockState().getValue(PantographBlock.FACING).getOpposite()));
		if (other instanceof PantographBlockEntity be) {
			if (wouldBeRecessive()) {
				CreateElectroEnergetics.LOGGER.info("becomeDouble from {} for {}", getBlockPos(), other.getBlockPos());
				color = be.color;
				for (int i = 0; i < getContainerSize(); i++) {
					ItemStack item = removeItemNoUpdate(0);
					if (be.canPlaceItem(be.lastSlot(), item)) {
						be.setItem(be.lastSlot(), item);
					} else if (level instanceof Level l) {
						Containers.dropItemStack(l, getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), item);
					}
				}
				clearContent();
			} else if (be.wouldBeRecessive()) {
				CreateElectroEnergetics.LOGGER.info("becomeDouble from {} for {}", getBlockPos(), other.getBlockPos());
				be.color = color;
				for (int i = 0; i < be.getContainerSize(); i++) {
					ItemStack item = be.removeItemNoUpdate(0);
					if (canPlaceItem(lastSlot(), item)) {
						setItem(lastSlot(), item);
					} else if (level instanceof Level l) {
						Containers.dropItemStack(l, be.getBlockPos().getX(), be.getBlockPos().getY(), be.getBlockPos().getZ(), item);
					}
				}
				be.clearContent();
			}
		}
	}

    private void handleOnClient(UnaryOperator<Vec3> positionTransform, UnaryOperator<Vec3> inversePositionTransform, Vec3 acceleration, float gravityCoefficient) {
        physicsController.globalPos = positionTransform.apply(worldPosition.getCenter());

        BlockState blockState = getBlockState();
        boolean isDouble = blockState.getValue(PantographBlock.DOUBLE);
        double pantographX = blockState.getValue(PantographBlock.FACING).getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.25 : 0.75;
        if (isDouble)
            pantographX = blockState.getValue(PantographBlock.FACING).getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1;
        Direction.Axis axis = blockState.getValue(FACING).getAxis();
        float halfPantoReach = 1.625f;

        Vec3 pantographPos = new Vec3(axis == Direction.Axis.X ? pantographX : 0.5, halfPantoReach, axis == Direction.Axis.Z ? pantographX : 0.5).add(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());

        Vec3 connectionPoint = null;

        level.getProfiler().push("searchForWires");

        for (Pair<InWorldNodeConnection, WireData> connection : WireRenderer.WIRE_CONNECTIONS) {
            if (connection.getSecond().wireType().getSag() != 0)
                continue;
            Vec3 start = connection.getFirst().node1().getPosition(level);
            Vec3 end = connection.getFirst().node2().getPosition(level);
            if (start == null || end == null)
                continue;
            start = inversePositionTransform.apply(start);
            end = inversePositionTransform.apply(end);
            float halfThickness = connection.getSecond().wireType().getThickness() * 0.5f;
            float progress = closestPointOnWire(start, end, pantographPos);
            Vec3 cp = checkCatenary(start, end, pantographPos, progress, halfPantoReach);
            if (cp != null) {
                if (connectionPoint == null || connectionPoint.y > cp.y)
					connectionPoint = cp.add(0, -halfThickness + 0.015f, 0);
            }
        }

        for (CatenaryConnection connection : WireRenderer.CATENARY) {
            Vec3 start = connection.getStartPos();
            Vec3 end = connection.getEndingPos();

            start = inversePositionTransform.apply(start);
            end = inversePositionTransform.apply(end);
            float progress = closestPointOnWire(start, end, pantographPos);
            Vec3 cp = checkCatenary(start, end, pantographPos, progress, halfPantoReach);
            if (cp != null) {
                if (connectionPoint == null || connectionPoint.y > cp.y) {
                    connectionPoint = cp;
                }
            }
        }

        level.getProfiler().popPush("debug");

        if (CEEConfigs.client().debugPantographRange.get())
            for (float x = -3; x < 3; x += 0.3f) {
                for (float y = -3; y < 3; y += 0.3f) {
                    for (float z = -3; z < 3; z += 0.3f) {
                        Vec3 pos = pantographPos.add(x, y, z);
                        if (checkCatenary(pos, pos, pantographPos, 0f, halfPantoReach) != null) {
                            pos = positionTransform.apply(pos);
                            level.addParticle(ParticleTypes.SCULK_CHARGE_POP, pos.x, pos.y, pos.z, 0, 0, 0);
                        }
                    }
                }
            }

        level.getProfiler().pop();

        if (connectionPoint != null) {
//            level.addParticle(ParticleTypes.ELECTRIC_SPARK, connectionPoint.x, connectionPoint.y, connectionPoint.z, 0, 0, 0);

            Vec3 contactCenter = worldPosition.getBottomCenter().with(Direction.Axis.Y, connectionPoint.y);
            contactCenter = contactCenter.relative(blockState.getValue(PantographBlock.FACING), isDouble ? -0.5 : -4/16.0);
            float offset = (float) (connectionPoint.distanceTo(contactCenter));

            float wireHeight = (float) (connectionPoint.y - worldPosition.getBottomCenter().y);
            if (offset > 1) wireHeight += (offset - 1) * 0.5f;

            physicsController.updateExtension(
                    1/20f,
                    wireHeight,
                    (float) acceleration.y,
                    gravityCoefficient,
                    isRecessive()
            );
        } else {
            physicsController.updateExtension(1/20f, 100, (float) acceleration.y, gravityCoefficient, isRecessive());
        }
    }

    private void handleOnServer(UnaryOperator<Vec3> positionTransform, UnaryOperator<Vec3> inversePositionTransform, Vec3 acceleration, float gravityCoefficient) {
        physicsController.globalPos = positionTransform.apply(worldPosition.getCenter());
        if (!(level instanceof ServerLevel serverLevel))
            return;
        InfrastructureSavedData sd = InfrastructureSavedData.load(serverLevel);

        boolean isDouble = getBlockState().getValue(PantographBlock.DOUBLE);
        double pantographX = getBlockState().getValue(PantographBlock.FACING).getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.25 : 0.75;
        if (isDouble)
            pantographX = getBlockState().getValue(PantographBlock.FACING).getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1;
        Direction.Axis axis = getBlockState().getValue(FACING).getAxis();
        float halfPantoReach = 1.625f;

        Vec3 pantographPos = new Vec3(axis == Direction.Axis.X ? pantographX : 0.5, halfPantoReach, axis == Direction.Axis.Z ? pantographX : 0.5).add(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());

        Vec3 connectionPoint = null;
        InWorldNodeConnection connectedConnection = null;
        float connectionProgress = 0;

        level.getProfiler().push("searchForWires");

        Vec3 inWorldPos = positionTransform.apply(pantographPos);
        long sectionPos = SectionPos.asLong(Mth.floor(inWorldPos.x) >> 4, Mth.floor(inWorldPos.y) >> 4, Mth.floor(inWorldPos.z) >> 4);
        for (Map.Entry<InWorldNodeConnection, ConnectionEntry> connection : sd.wireSimulationState.getConnectionsInSection(sectionPos).entrySet()) {
            if (connection.getValue().wireData.wireType().getSag() != 0)
                continue;
            Vec3 start = connection.getKey().node1().getPosition(level);
            Vec3 end = connection.getKey().node2().getPosition(level);
            if (start == null || end == null)
                continue;
            start = inversePositionTransform.apply(start);
            end = inversePositionTransform.apply(end);
            float halfThickness = connection.getValue().wireData.wireType().getThickness() * 0.5f;
            float progress = closestPointOnWire(start, end, pantographPos);
            Vec3 cp = checkCatenary(start, end, pantographPos, progress, halfPantoReach);
            if (cp != null) {
                if (connectionPoint == null || connectionPoint.y > cp.y) {
                    connectionPoint = cp.add(0, -halfThickness + 0.015f, 0);
                    connectedConnection = connection.getKey();
                    connectionProgress = progress;
                }
            }
        }

        for (CatenaryConnection connection : sd.getAllCatenaryConnections()) {
            Vec3 start = connection.getStartPos();
            Vec3 end = connection.getEndingPos();

            start = inversePositionTransform.apply(start);
            end = inversePositionTransform.apply(end);
            float progress = closestPointOnWire(start, end, pantographPos);
            Vec3 cp = checkCatenary(start, end, pantographPos, progress, halfPantoReach);
            if (cp != null) {
                if (connectionPoint == null || connectionPoint.y > cp.y) {
                    connectionPoint = cp;
                    connectedConnection = new InWorldNodeConnection(
                            new InWorldNode(0, connection.pos1()),
                            new InWorldNode(0, connection.pos2()));
                    connectionProgress = progress;
                }
            }
        }

        level.getProfiler().pop();

        if (connectionPoint != null) {
            Vec3 contactCenter = worldPosition.getBottomCenter().with(Direction.Axis.Y, connectionPoint.y);
            contactCenter = contactCenter.relative(getBlockState().getValue(PantographBlock.FACING), isDouble ? -0.5 : -4/16.0);
            float offset = (float) (connectionPoint.distanceTo(contactCenter));

            float wireHeight = (float) (connectionPoint.y - worldPosition.getBottomCenter().y);
            if (offset > 1) wireHeight += (offset - 1) * 0.5f;

            physicsController.updateExtension(
                    1/20f,
                    wireHeight,
                    (float) acceleration.y,
                    gravityCoefficient,
                    isRecessive());
            if (handle == null || !handle.valid())
                handle = sd.wireSimulationState.createHandle("Pantograph-"+worldPosition.toShortString());

            if (lastConnection == null) {
                sd.wireSimulationState.removeCutsFrom(handle);
                attachedNode = sd.wireSimulationState.createCut(handle, connectedConnection, connectionProgress);
            } else if (!lastConnection.equals(connectedConnection) ||
                    !sd.wireSimulationState.cutExists(handle, attachedNode)) {
                sd.wireSimulationState.removeCutsFrom(handle);
                attachedNode = sd.wireSimulationState.createCut(handle, connectedConnection, connectionProgress);
            } else {
                sd.wireSimulationState.relocateCut(handle, attachedNode, connectionProgress);
            }

            lastConnection = connectedConnection;

        } else {
            physicsController.updateExtension(1/20f, 100, (float) acceleration.y, gravityCoefficient, isRecessive());
            disconnect();
        }
    }

    private void disconnect() {
        if (level instanceof ServerLevel serverLevel && handle != null) {
            if (handle.valid()) {
                InfrastructureSavedData sd = InfrastructureSavedData.load(serverLevel);
                sd.wireSimulationState.invalidateHandle(handle);
            }
            attachedNode = null;
            handle = null;
            lastConnection = null;
        }
    }

    float closestPointOnWire(Vec3 start, Vec3 end, Vec3 checker) {
        double t = 0;

        Vec3 ab = end.subtract(start);
        Vec3 ap = checker.subtract(start);
        double denom = ab.lengthSqr();
        if (denom != 0)
            t = ap.dot(ab) / denom;
        if (t < -0.01)
            return 0;
        if (t > 1.01)
            return 1;
        return (float) t;
    }

    Vec3 checkCatenary(Vec3 start, Vec3 end, Vec3 pantographPos, float closestPointOnWire, float halfPantoReach) {

        Vec3 closest = VecHelper.lerp(closestPointOnWire, start, end);
        Vec3 distance = pantographPos.subtract(closest);
        if (distance.y > 0.8)
            return null;

        distance = VecHelper.rotate(distance, getBlockState().getValue(FACING).toYRot() + 90, Direction.Axis.Y);
        float xTol = (float) ((-distance.y + halfPantoReach) * 0.2f + 0.125f);
        if (Math.abs(distance.z) < 1.5 && Math.abs(distance.x) < xTol && Math.abs(distance.y) < halfPantoReach)
            return closest;

        return null;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        power = tag.getDouble("power");
        springState = SpringMovementState.byName(tag.getString("springState"), SpringMovementState.NONE);
        color = DyeColor.byName(tag.getString("Color"), DyeColor.WHITE);

        ListTag inventoryListTag = tag.getList("inventory", Tag.TAG_COMPOUND);

        inventory.clear();

        if (!inventoryListTag.isEmpty()) {
            inventory.ensureCapacity(inventoryListTag.size());
            inventory.addAll(
                inventoryListTag.stream()
                    .map(tag1 -> {
						if (tag1.getType() == CompoundTag.TYPE)
				            return ItemStack.parseOptional(registries, ((CompoundTag) tag1));
						return ItemStack.EMPTY;
					})
                    .filter(itemStack -> !itemStack.isEmpty())
                    .toList()
            );
        }

        physicsController.deserializeNBT(registries, tag.getCompound("physics"));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("power", power);
        tag.putString("springState", springState.getSerializedName());
        tag.putString("Color", color.getSerializedName());

        ListTag inventoryList = new ListTag(inventory.size());

        inventory.removeIf(ItemStack::isEmpty);

        for (ItemStack itemStack : inventory) {
            inventoryList.add(itemStack.save(registries));
        }

		tag.put("inventory", inventoryList);
        tag.put("physics", physicsController.serializeNBT(registries));
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return super.createRenderBoundingBox().inflate(3);
    }

    @Override
    public void clearContent() {
        inventory.clear();
        updateControlMode();
    }

    @Override
    public int getContainerSize() {
        return inventory.size() + 1;
    }

    public int lastSlot() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == inventory.size()) return ItemStack.EMPTY;
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == inventory.size()) return ItemStack.EMPTY;
        ItemStack itemStack = inventory.get(slot);
        if (itemStack.getCount() == amount) inventory.remove(slot);
        updateControlMode();
        return itemStack.split(amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == inventory.size()) return ItemStack.EMPTY;
        ItemStack itemStack = getItem(slot).copy();
        inventory.remove(slot);
//        updateControlMode();
        return itemStack;
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            inventory.remove(slot);
			return;
        }
        if (slot < inventory.size()) inventory.set(slot, itemStack);
        else inventory.add(itemStack);
        updateControlMode();
    }

    public void updateControlMode() {
        if (level != null)
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(PantographBlock.CONTROL_MODE, controlModeFromItems()));
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // If empty, allow any control item. If not, allow combining redstone and remote

        if (
            inventory.isEmpty() && slot == 0 && (
                stack.is(CEETags.PANTOGRAPH_MOTOR) ||
                stack.is(CEETags.PANTOGRAPH_REDSTONE) ||
                stack.is(CEETags.PANTOGRAPH_REMOTE)
            )
        ) return true;

        if (inventory.size() == 1 && slot == 1) {
            if (
                inventory.getFirst().is(CEETags.PANTOGRAPH_REMOTE) &&
                stack.is(CEETags.PANTOGRAPH_REDSTONE)
            ) return true;

			return
                inventory.getFirst().is(CEETags.PANTOGRAPH_REDSTONE) &&
                stack.is(CEETags.PANTOGRAPH_REMOTE);
        }
        return false;
    }

    public void giveBackItems(Player player) {
        for (ItemStack itemStack : inventory) {
            player.getInventory().placeItemBackInInventory(itemStack);
        }
        clearContent();
    }

    public PantographControlMode controlModeFromItems() {
        PantographBlockEntity targetInventory; // If recessive, control mode depends on the other one
		if (
            isRecessive() &&
            level.getBlockEntity(
                    getBlockPos().relative(getBlockState().getValue(PantographBlock.FACING).getOpposite())
            ) instanceof PantographBlockEntity be1
        ) targetInventory = be1;
		else targetInventory = this;

		ArrayList<ItemStack> inventory1 = targetInventory.inventory;
        if (inventory1.isEmpty()) return PantographControlMode.MANUAL;

        if (
            inventory1.size() == 2 && (
                (inventory1.getFirst().is(CEETags.PANTOGRAPH_REMOTE) && inventory1.get(1).is(CEETags.PANTOGRAPH_REDSTONE)) ||
                (inventory1.getFirst().is(CEETags.PANTOGRAPH_REDSTONE) && inventory1.get(1).is(CEETags.PANTOGRAPH_REMOTE))
            )
        ) return PantographControlMode.REMOTE_REDSTONE;

        if (inventory1.size() != 1) return PantographControlMode.MANUAL;
        if (inventory1.getFirst().is(CEETags.PANTOGRAPH_MOTOR)) return PantographControlMode.MOTORIZED;

        if (inventory1.getFirst().is(CEETags.PANTOGRAPH_REMOTE)) return PantographControlMode.REMOTE;
        if (inventory1.getFirst().is(CEETags.PANTOGRAPH_REDSTONE)) return PantographControlMode.REDSTONE;

        return PantographControlMode.MANUAL;
    }

    public boolean isRecessive() {
        BlockState state = getBlockState();
        return state.getValue(PantographBlock.DOUBLE) && (state.getValue(PantographBlock.FACING).get2DDataValue() & 2) == 0;
    }

    public boolean wouldBeRecessive() {
        BlockState state = getBlockState();
        return (state.getValue(PantographBlock.FACING).get2DDataValue() & 2) == 0;
    }

    public boolean isDominant() {
        BlockState state = getBlockState();
        return state.getValue(PantographBlock.DOUBLE) && (state.getValue(PantographBlock.FACING).get2DDataValue() & 2) != 0;
    }



    public enum SpringMovementState implements StringRepresentable {
        NONE, // Not powered
        IDLE, // Powered, but at endpoint
        STALLED, // Powered, but doesn't have enough power to move
        MOVING; // Powered and moving

        /// Based on [DyeColor]
        public static final EnumCodec<SpringMovementState> CODEC = StringRepresentable.fromEnum(SpringMovementState::values);

        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }

        public static SpringMovementState byName(String translationKey, @Nullable SpringMovementState fallback) {
            SpringMovementState state = CODEC.byName(translationKey);
            return state != null ? state : fallback;
        }
    }
}
