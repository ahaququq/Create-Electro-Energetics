package com.george_vi.electroenergetics.content.railway_electrification.pantograph;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import static com.george_vi.electroenergetics.content.railway_electrification.pantograph.PantographBlock.*;
import static net.minecraft.world.level.block.Block.UPDATE_CLIENTS;

public class PantographDevice extends SimpleElectricalDevice {
    public PantographBlockEntity be;

    public PantographDevice(Level level, BlockPos pos, DevicesSavedData deviceSD, SimulatedDeviceType<?> type) {
        super(level, pos, deviceSD, type);
    }

    /// # Connection Layout:
    /// ## Single manual
    /// ```
    /// [Catenary]───[0]
    /// ```
    /// ## Single motorized
    /// ```
    ///              [1]──────╮
    /// [Catenary]───[0]   [Motor]
    ///              [2]──────╯
    /// ```
    /// ## Double manual
    /// ```
    /// [Catenary]───[0]───[0]
    /// ```
    /// ## Double motorized
    /// ```
    ///              [1]───[2]──────╮
    /// [Catenary]───[0]───[0]   [Motor]
    ///              [2]───[1]──────╯
    /// ```

    @Override
    public void preTick(BridgeCollector bridges) {
        if (this.be == null && level.isLoaded(pos))
            if (level.getBlockEntity(pos) instanceof PantographBlockEntity newBE)
                this.be = newBE;

        if (this.be != null) {
            if (this.be.isRemoved())
                this.be = null;
            else {
                Direction facing = be.getBlockState().getValue(FACING);
                if (be.getBlockState().getValue(CONTROL_MODE).isMotorized()) {
                    if (be.isRecessive()) { // Recessive just connects to its pair
                        bridges.bridge(
                                new InWorldNode(1, pos),
                                new InWorldNode(2, pos.relative(facing.getOpposite())),
                                0.01
                        );
                        bridges.bridge(
                                new InWorldNode(2, pos),
                                new InWorldNode(1, pos.relative(facing.getOpposite())),
                                0.01
                        );
                    } else {
                        double motorResistance = 72; // Base resistance, 1/3 A (8W) @ 24 V idle

                        if (be.springState == PantographBlockEntity.SpringMovementState.MOVING) { // Lower resistance once moving
                            motorResistance = 12; // 2A (48W) @ 24 V to move
                        }

                        bridges.builder(pos).resistor(1, 2, motorResistance);
                    }
                }

                if (be.isDominant()) { // Dominant connects the contacts
                    bridges.bridge(
                            new InWorldNode(0, pos),
                            new InWorldNode(0, pos.relative(facing.getOpposite())),
                            0.01
                    );
                    if (be.attachedNode != null && be.physicsController.distanceFromWire() < 0.001)
                        bridges.builder(pos).resistor(new InWorldNode(0, pos), be.attachedNode, 0.01);
                } else if ( // Single does it by itself
                    !be.getBlockState().getValue(DOUBLE) &&
                        be.attachedNode != null &&
                        be.physicsController.distanceFromWire() < 0.001
                ) bridges.builder(pos).resistor(new InWorldNode(0, pos), be.attachedNode, 0.01);
            }
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        if (this.be != null) {
            BlockState state = be.getBlockState();

            if (!state.getValue(CONTROL_MODE).isMotorized()) {
                be.springState = PantographBlockEntity.SpringMovementState.NONE;
                updateBlockEntity(state);
                return;
            }

            double voltage = results.getVoltageAt(pos, 1, 2);
            double current = results.getCurrentThrough(pos, 1, 2);

            double power = voltage * Math.abs(current);

            be.power = Math.abs(power);

            if (Math.abs(power) < 0.01) {
                be.springState = PantographBlockEntity.SpringMovementState.NONE;
                updateBlockEntity(state);
                return;
            }

            float speed = (float) (power * 0.01);

			if ((be.physicsController.springPosition >= be.physicsController.getSpringUpperLimit() && speed >= 0) ||
                (be.physicsController.springPosition <= be.physicsController.getSpringLowerLimit() && speed <= 0)) {
                be.springState = PantographBlockEntity.SpringMovementState.IDLE;
                updateBlockEntity(state);
                return;
            }

            if (Math.abs(power) < 5) {
                be.springState = PantographBlockEntity.SpringMovementState.STALLED;
                updateBlockEntity(state);
                return;
            }

            be.springState = PantographBlockEntity.SpringMovementState.MOVING;
            be.physicsController.springPosition += speed * 0.05f;

            updateBlockEntity(state);
        }
    }

    private void updateBlockEntity(BlockState state) {
        be.setChanged();
        level.sendBlockUpdated(pos, state, state, UPDATE_CLIENTS);

        BlockPos relative = be.getBlockPos().relative(state.getValue(FACING).getOpposite());
        if (be.isDominant() && level.getBlockEntity(relative) instanceof PantographBlockEntity be1) {
            be1.springState = be.springState;
            be1.power = be.power;
            be1.setChanged();
            BlockState blockState = level.getBlockState(relative);
            level.sendBlockUpdated(relative, blockState, blockState, UPDATE_CLIENTS);
        }
    }
}
