package com.george_vi.electroenergetics.content.railway_electrification.pantograph;

import com.george_vi.electroenergetics.CEENodeConfigurations;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import net.createmod.catnip.lang.Lang;
import net.minecraft.util.StringRepresentable;

public enum PantographControlMode implements StringRepresentable {
    MANUAL, // Default, activated by hand
    REDSTONE, // Redstone activated, accepts direct redstone power
    REMOTE, // Remote link activated, has a frequency pair
    MOTORIZED; // Electrically activated, has two more nodes

    public boolean isManual() {
        return this == MANUAL;
    }

    public boolean isRedstone() {
        return this == REDSTONE;
    }

    public boolean isRemote() {
        return this == REMOTE;
    }

    public boolean isMotorized() {
        return this == MOTORIZED;
    }

    public NodeConfigurator getNodes() {
        if (this == MOTORIZED)
            return CEENodeConfigurations.PANTOGRAPH_MOTORIZED;
        else
            return CEENodeConfigurations.PANTOGRAPH_MANUAL;
    }

    @Override
    public String getSerializedName() {
        return Lang.asId(name());
    }
}
