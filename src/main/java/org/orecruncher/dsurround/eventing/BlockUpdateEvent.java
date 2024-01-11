package org.orecruncher.dsurround.eventing;

import net.minecraft.core.BlockPos;

import java.util.Collection;

public record BlockUpdateEvent(Collection<BlockPos> updates) {

}
