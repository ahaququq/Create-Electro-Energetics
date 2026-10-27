package com.george_vi.electroenergetics.content.railway_electrification.pantograph;

import com.george_vi.electroenergetics.CEENodeConfigurations;
import com.george_vi.electroenergetics.foundation.nodes.NodeConfigurator;
import net.createmod.catnip.lang.Lang;
import net.minecraft.util.StringRepresentable;

public enum PantographControlMode implements StringRepresentable {
    NONE("error"), // Needs to be calculated in the next tick
    MANUAL("manual"), // Default, activated by hand
    REDSTONE("redstone"), // Redstone activated, accepts direct redstone power
    REMOTE("redstone.remote"), // Remote link activated, has a frequency pair
    REMOTE_REDSTONE("redstone.remote_redstone"), // Both of the above
    MOTORIZED("motorized"); // Electrically activated, has two more nodes

    private final String translationName;

//    public boolean shouldObfuscate() { return this == NONE; } // Could be used for Modrinth build
    public boolean shouldObfuscate() { return false; } // For debug
	public boolean needsUpdating() { return this == NONE; }
    public boolean isManual() { return this == MANUAL; }
    public boolean isRedstone() { return this == REDSTONE || this == REMOTE_REDSTONE; }
    public boolean isRemote() { return this == REMOTE || this == REMOTE_REDSTONE; }
    public boolean isMotorized() { return this == MOTORIZED; }

    PantographControlMode(String translationName) {
        this.translationName = translationName;
    }

    public NodeConfigurator getNodes() {
        if (isMotorized())
            return CEENodeConfigurations.PANTOGRAPH_MOTORIZED;
        else
            return CEENodeConfigurations.PANTOGRAPH_MANUAL;
    }

    @Override
    public String getSerializedName() {
        return Lang.asId(name());
    }

	public String getTranslationName() {
		return translationName;
	}
}
