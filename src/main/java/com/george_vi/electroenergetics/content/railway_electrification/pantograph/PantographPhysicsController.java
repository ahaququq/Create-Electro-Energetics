package com.george_vi.electroenergetics.content.railway_electrification.pantograph;

import com.george_vi.electroenergetics.CreateElectroEnergetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

public class PantographPhysicsController implements INBTSerializable<CompoundTag> {
	public Level level;
	public boolean isInWorld;
	public boolean isDouble;
	public Direction facing;
	public BlockPos localPos;
	public Vec3 globalPos;
	public float nextCollision = 0;

	public float lastWireHeight = Float.POSITIVE_INFINITY;
	public boolean wasConnected = false;

	public float length;
	public float velocity = 0;
	public float acceleration = 0;

	/// Main spring, powers the opening and closing
	public float springPosition;

	/// Raw, direct forces
	public float forces = 0;

	public PantographPhysicsController(Level level, boolean isDouble, Direction facing, BlockPos localPos, Vec3 globalPos, boolean isInWorld) {
		this.level = level;
		this.isDouble = isDouble;
		this.facing = facing;
		this.localPos = localPos;
		this.globalPos = globalPos;
		this.isInWorld = isInWorld;

		// Closed by default
		this.length = getLowerLimit();
		this.springPosition = getSpringLowerLimit();
	}

	// Configuration, some of those may be added to the config

	/// Minimal position, blocks above bottom center
	public float getLowerLimit() { return isDouble ? 21/16f : 16/16f; }
	/// Maximal position
	public float getUpperLimit() { return 3 + 1/16f; }
	/// Mass, currently mostly useless, divides force, may come in handy with joining different force sources with different pantograph models
	public float getMass() { return 1f; }
	/// Friction, used to limit terminal velocity
	public float getFriction() { return 0.4f; }
	/// Bounce - how much velocity is kept after collision. May depend on collision type in the future
	public float getBounce() { return 0.6f; }

	/// How much wider should the springs movement range be than the motion range
	public float getSpringRangeOffset() {
		float range = getUpperLimit() - getLowerLimit();
		return range * (isDouble ? 0.6f : 0.5f);
	}
	/// Spring strength, as force at 1 block of difference
	public float getSpringStrength() { return 4; }
	public float getSpringLowerLimit() { return getLowerLimit() - getSpringRangeOffset(); }
	public float getSpringUpperLimit() { return getUpperLimit() + getSpringRangeOffset(); }

	/// Gravity force, gets oriented with the sublevel
	public float getGravity() { return 2.0f; }

	public float distanceFromWire() {
		if (lastWireHeight == Float.POSITIVE_INFINITY) return Float.POSITIVE_INFINITY; // Just in case
		return Math.max(lastWireHeight - length - 0.001f, 0);
	}

	public float interpolate(float partialTicks) {
		float dt = Math.min(nextCollision, partialTicks * 0.05f);

		return length + (velocity + acceleration * dt) * dt;
	}

	public float interpolateRendering(float partialTicks) {
		return toRenderingState(interpolate(partialTicks));
	}

	public float toRenderingState(float length) {
		return inverseKinematics(localPos.getBottomCenter().add(0, length, 0));
	}

	private float inverseKinematics(Vec3 connectionPoint) {
		float lo = 0;
		float hi = 2.7f;
		for (int i = 0; i < 20; i++) {
			float m1 = lo + (hi - lo) / 3;
			float m2 = hi - (hi - lo) / 3;
			if (Math.abs(getConnectorPos(m1).y - connectionPoint.y) < Math.abs(getConnectorPos(m2).y - connectionPoint.y))
				hi = m2;
			else
				lo = m1;
		}
		return (lo + hi) / 2;
	}

	Vec3 getConnectorPos(float extensionState) {
		Direction.Axis axis = facing.getAxis();
		if (isDouble) {
			float lowerArmRadians = (-90 + extensionState * 27) * Mth.DEG_TO_RAD;
			double armHingePosY = Mth.cos(lowerArmRadians) * 1.5 + 0.5;
			double armHingePosX = Mth.sin(lowerArmRadians) * 1.5;

			float b = (float) (-0.4f - Math.abs(armHingePosX));
			float a = Mth.sqrt(-b * b + 2f * 2f);

			double pantographX = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 1;

			Vec3 connectorPlatePos = new Vec3(axis == Direction.Axis.X ? pantographX : 0.5f, 0.1875 + a + armHingePosY, axis == Direction.Axis.Z ? pantographX : 0.5f);
			connectorPlatePos = connectorPlatePos.add(localPos.getX(), localPos.getY(), localPos.getZ());
			return connectorPlatePos;
		}

		float lowerArmRadians = (-75 + extensionState * 30) * Mth.DEG_TO_RAD;

		double armHingePosY = Mth.cos(lowerArmRadians) * 1.875 + 0.5;
		double armHingePosX = Mth.sin(lowerArmRadians) * 1.875;

		float upperArmRadians = (89 - extensionState * 50) * Mth.DEG_TO_RAD;
		double pantographX = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.25 : 0.75;
		Vec3 connectorPlatePos = new Vec3((axis == Direction.Axis.X ? pantographX : 0.5), armHingePosY, armHingePosX + (axis == Direction.Axis.Z ? pantographX : 0.5))
				.add(0, Mth.cos(upperArmRadians) * 1.9, Mth.sin(upperArmRadians) * 1.9);
		connectorPlatePos = connectorPlatePos.add(localPos.getX(), localPos.getY(), localPos.getZ());
		return connectorPlatePos;
	}

	public void updateExtension(float dt, float wireHeight, float accelerationUp, float gravityCoefficient, boolean recessive) {
		springPosition = Mth.clamp(springPosition, getSpringLowerLimit(), getSpringUpperLimit());

		boolean newWasConnected = distanceFromWire() == 0;

		if (newWasConnected != wasConnected) {
			// TODO: Add arcing/connection sounds here (NOT impact sounds)
			wasConnected = newWasConnected;
		}

		if (wireHeight <= getUpperLimit()) lastWireHeight = wireHeight;
		else lastWireHeight = Float.POSITIVE_INFINITY;

		if (level == null) {
			CreateElectroEnergetics.LOGGER.warn("PantographPhysicsController.level is null");
			return;
		}
		level.getProfiler().push("updateExtension");
		if (isDouble && recessive) { // TODO: Handle recessive pantographs on contraptions
			// Recessive pantograph
			level.getProfiler().pop();
			return;
		}

		// Get collision limits
		float lowerLimit = getLowerLimit();
		float upperLimit = Math.min(getUpperLimit(), wireHeight);

		float newAcceleration
			= (
				(
					forces + // Forces are applied directly
					getSpringStrength() * (springPosition - length) // Spring depends on current extension
				) / getMass() +
				accelerationUp * 10 + // It looks like the external acceleration is in 0.1 m/s²
				getGravity() * gravityCoefficient // Gravity is multiplied by gravityCoefficient, which is just the Y component of the down vector
			) * getFriction() +
			velocity * (-1 + getFriction()) / (4f * dt);

		float newVelocity = velocity + newAcceleration * 2f * dt;

		// Average the current and previous velocities to calculate the velocity between then and now
		float newLength = length + (newVelocity + velocity) * 0.5f * dt;

		float collisionLower = getCollision(length, lowerLimit, dt);
		float collisionUpper = getCollision(length, upperLimit, dt);


		boolean lowerFirst = collisionLower < collisionUpper;
		float firstCollision = lowerFirst ? collisionLower : collisionUpper;

		if (firstCollision < dt) {
			float firstCollisionLimit = lowerFirst ? lowerLimit : upperLimit;

			float velocityOnImpact = velocity + acceleration * 2f * firstCollision;
			float positionOnImpact = length + (velocity + velocityOnImpact) * 0.5f * firstCollision;

			// Allow clipping through upper limit going down and through lower going up
			boolean collisionCounts = velocityOnImpact <= 0 == lowerFirst;

			if (collisionCounts) {
				nextCollision = firstCollision;
				newVelocity = -velocityOnImpact * getBounce(); // Bounce
				newLength = firstCollisionLimit;

				impactSound(velocityOnImpact);

				acceleration = newAcceleration;
				velocity = newVelocity;
				length = newLength;
				updateRecessive();
				level.getProfiler().pop();
				return; // Needs a "recursive step"
			}

			float secondCollision = lowerFirst ? collisionUpper : collisionLower;

			if (secondCollision < dt) {
				float secondCollisionLimit = lowerFirst ? upperLimit : lowerLimit;

				velocityOnImpact = velocity + acceleration * 2f * firstCollision;
				positionOnImpact = length + (velocity + velocityOnImpact) * 0.5f * firstCollision;

				collisionCounts = velocityOnImpact <= 0 != lowerFirst;

				if (collisionCounts) {
					nextCollision = secondCollision;
					newVelocity = -velocityOnImpact * getBounce(); // Bounce;
					newLength = secondCollisionLimit;

					impactSound(velocityOnImpact);

					acceleration = newAcceleration;
					velocity = newVelocity;
					length = newLength;
					updateRecessive();
					level.getProfiler().pop();
					return; // Also needs a "recursive step"
				}
			}
		}

		nextCollision = dt * 5f;

		if (newLength > upperLimit) {
			newLength = upperLimit;
			newVelocity = 0;
			nextCollision = 0;
		} else if (newLength < lowerLimit) {
			newLength = lowerLimit;
			newVelocity = 0;
			nextCollision = 0;
		}

		acceleration = newAcceleration;
		velocity = newVelocity;
		length = newLength;
		updateRecessive();
		level.getProfiler().pop();
	}

	private void updateRecessive() {
		if (isDouble && isInWorld) { // TODO: Handle recessive pantographs on contraptions
			// Dominant pantograph writing to recessive

			BlockEntity blockEntity = level.getBlockEntity(localPos.relative(facing.getOpposite()));

			if (blockEntity instanceof PantographBlockEntity other) {
				if (other.physicsController == this) return;

				other.physicsController.syncFrom(this);
				return;
			}
			level.setBlock(localPos, level.getBlockState(localPos).setValue(PantographBlock.DOUBLE, false), 0);
			CreateElectroEnergetics.LOGGER.warn(
					"Double pantograph at {} {} {} lost it's companion and is reverting to single",
					localPos.getX(),
					localPos.getY(),
					localPos.getZ()
			);
		}
	}

	private void impactSound(float velocityOnImpact) {
		if (level.isClientSide && Mth.abs(velocityOnImpact) > 0.05f) {
			Vec3 pos = globalPos.add(0, length - 0.5, 0).relative(facing.getOpposite(), 0.5);

//			level.addParticle(ParticleTypes.NOTE, pos.x, pos.y, pos.z, 0, 0, 0); // Preview sound source position

			level.playLocalSound(
					pos.x,
					pos.y,
					pos.z,
					SoundEvents.CHAIN_HIT,
					SoundSource.BLOCKS,
					(float) (Math.log(Mth.abs(velocityOnImpact) * 5)),
					1.0f,
					false
			);
		}
	}

	private float getCollision(float extensionPrevLength, float hitTarget, float dt) {
		level.getProfiler().push("collision");
		// Altered quadratic formula, detects collisions with hitTarget between now (0) and dt
		float preDelta = velocity * velocity - 4f * acceleration * (extensionPrevLength - hitTarget);
		if (preDelta < 0) {
			level.getProfiler().pop();
			return dt; // No collisions
		}
		float delta = Mth.sqrt(preDelta);
		float collision0 = (-velocity - delta) / (2 * acceleration);
		float collision1 = (-velocity + delta) / (2 * acceleration);
		float firstCollision = Math.min(collision0, collision1);
		float secondCollision = Math.max(collision0, collision1);
		if (firstCollision > 0) {
			level.getProfiler().pop();
			return Math.min(firstCollision, dt); // Return first collision if in range
		}
		if (secondCollision > 0) {
			level.getProfiler().pop();
			return Math.min(secondCollision, dt); // Return the second if first was before range
		}
		level.getProfiler().pop();
		return dt; // Both were before range
	}

	@Override
	public CompoundTag serializeNBT(@Nullable HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putFloat("nextCollision", nextCollision);
		tag.putFloat("length", length);
		tag.putFloat("velocity", velocity);
		tag.putFloat("acceleration", acceleration);
		tag.putFloat("forces", forces);
		tag.putFloat("springPosition", springPosition);
		tag.putFloat("lastWireHeight", lastWireHeight);
		tag.putBoolean("wasConnected", wasConnected);
		return tag;
	}

	@Override
	public void deserializeNBT(@Nullable HolderLookup.Provider provider, CompoundTag tag) {
//		nextCollision  = tag.getFloat("nextCollision");
//		length         = tag.getFloat("length");
//		velocity       = tag.getFloat("velocity");
//		acceleration   = tag.getFloat("acceleration");
		forces         = tag.getFloat("forces");
		springPosition = tag.getFloat("springPosition");
		lastWireHeight = tag.getFloat("lastWireHeight");
		wasConnected   = tag.getBoolean("wasConnected");
	}

	public void syncFrom(PantographPhysicsController other) {
		nextCollision  = other.nextCollision;
		length         = other.length;
		velocity       = other.velocity;
		acceleration   = other.acceleration;
		forces         = other.forces;
		springPosition = other.springPosition;
		lastWireHeight = other.lastWireHeight;
		wasConnected   = other.wasConnected;
	}

	public boolean isExtended() {
		return springPosition >= getSpringUpperLimit() - 0.01;
	}

	public void toggle() {
		if (isExtended()) springPosition = getSpringLowerLimit();
		else springPosition = getSpringUpperLimit();
	}

	public void setExtended(boolean extended) {
		springPosition = extended ? getSpringUpperLimit() : getSpringLowerLimit();
	}
}
