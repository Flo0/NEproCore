package com.gestankbratwurst.core.mmcore.protocol.holograms.impl;

import com.gestankbratwurst.core.mmcore.protocol.holograms.AbstractHologram;
import com.gestankbratwurst.core.mmcore.protocol.holograms.AbstractHologramManager;
import com.gestankbratwurst.core.mmcore.protocol.holograms.IHologramLine;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class Hologram extends AbstractHologram {

  private final Set<ClickableEntity> clickableEntitys;

  public Hologram(final Location baseLocation, final Predicate<Player> playerFilter,
      final AbstractHologramManager manager, final UUID uid) {
    super(baseLocation, playerFilter, manager, uid);
    this.clickableEntitys = Sets.newHashSet();
  }

  @Override
  public void appendTextLine(final String text) {
    this.appendLine(new HologramTextLine(this.getBaseLocation().clone().subtract(0, 0.25 * this.getSize(), 0), text, this));
    if (this.clickable && this.clickableEntitys.size() < this.getSize() / 4) {
      this.fillClickableEntities();
    }
  }

  @Override
  public void appendDynamicTextLine(final Function<Player, String> dynamicLine) {
    this.appendLine(new DynamicHologramTextLine(this.getBaseLocation().clone().subtract(0, 0.25 * this.getSize(), 0), dynamicLine, this));
    if (this.clickable && this.clickableEntitys.size() < this.getSize() / 4) {
      this.fillClickableEntities();
    }
  }

  @Override
  public void appendItemLine(final ItemStack item) {
    this.appendLine(
        new HologramItemLine(this.getBaseLocation().clone().subtract(0, 0.25 * this.getSize(), 0),
            item, this));
    if (this.clickable && this.clickableEntitys.size() < this.getSize() / 4) {
      this.fillClickableEntities();
    }
  }

  private void fillClickableEntities() {
    final Set<Player> viewers = this.getViewers();
    for (final Player player : viewers) {
      this.hideClickableEntities(player);
    }
    int dsize = this.getSize() / 4;
    dsize = Math.max(dsize, 1);
    while (this.clickableEntitys.size() < dsize) {
      this.clickableEntitys.add(new ClickableEntity(this.getBaseLocation().clone().subtract(0, this.clickableEntitys.size() * -1.5, 0)));
    }
    for (final Player player : viewers) {
      this.showClickableEntities(player);
    }
    this.registerClickableEntities();
  }

  @Override
  public void setClickable() {
    if (this.clickable) {
      return;
    }
    this.clickable = true;
    this.fillClickableEntities();
  }

  @Override
  protected void showClickableEntities(final Player player) {
    for (final ClickableEntity clickable : this.clickableEntitys) {
      clickable.sendSpawnPacket(player);
    }
  }

  @Override
  protected void hideClickableEntities(final Player player) {
    for (final ClickableEntity clickable : this.clickableEntitys) {
      clickable.sendDespawnPacket(player);
    }
  }

  private static final class ClickableEntity extends Slime {

    private ClickableEntity(final Location location) {
      super(EntityType.SLIME, ((CraftWorld) location.getWorld()).getHandle());
      this.moveTo(location.getX(), location.getY(), location.getZ());
      this.setInvisible(true);
      this.setSize(2, true);
      this.setInvulnerable(true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
      return false;
    }

    @Override
    protected boolean damageEntity0(final DamageSource damagesource, final float f) {
      return false;
    }


    public void sendSpawnPacket(final Player player) {
      final ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;
      connection.send(new ClientboundAddEntityPacket(this));
      connection.send(new ClientboundSetEntityDataPacket(this.getId(), this.entityData, true));
    }

    public void sendDespawnPacket(final Player player) {
      ((CraftPlayer) player).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(this.getId()));
    }
  }

  @Override
  protected Set<Integer> getClickableEntityIds() {
    return this.clickableEntitys.stream().map(Entity::getId).collect(Collectors.toSet());
  }

  @Override
  protected void moveHologram(final Vector direction) {
    for (final Player viewer : this.getViewers()) {
      for (final IHologramLine<?> line : this.lines) {
        line.sendMove(viewer, direction);
      }
    }
  }
}
