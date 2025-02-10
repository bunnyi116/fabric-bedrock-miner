package com.github.bunnyi116.bedrockminer.task;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Objects;
import java.util.Optional;

public class TaskBlock {
    public final ClientWorld world;
    public final BlockPos pos;

    public TaskBlock(ClientWorld world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
    }

    public BlockState getBlockState() {
        return this.world.getBlockState(pos);
    }

    public Block getBlock() {
        return this.getBlockState().getBlock();
    }

    public Text getBlockName() {
        return this.getBlock().getName();
    }

    public String getBlockNameString() {
        return this.getBlockName().getString();
    }

    public <T extends Comparable<T>> boolean contains(Property<T> property) {
        return this.getBlockState().contains(property);
    }

    public <T extends Comparable<T>> T get(Property<T> property) {
        return this.getBlockState().get(property);
    }

    public <T extends Comparable<T>> Optional<T> getOrEmpty(Property<T> property) {
        return this.getBlockState().getOrEmpty(property);
    }

    public boolean isOf(Block block) {
        return this.getBlockState().isOf(block);
    }

    public boolean isOf(RegistryEntry<Block> blockEntry) {
        return this.getBlockState().isOf(blockEntry);
    }

    public boolean isAir() {
        return this.getBlockState().isAir();
    }

    public boolean isReplaceable() {
        return this.getBlockState().isReplaceable();
    }

    public boolean isToolRequired() {
        return this.getBlockState().isToolRequired();
    }

    public BlockPos offset(Direction direction) {
        return this.pos.offset(direction);
    }

    public BlockPos offset(Direction direction, int distance) {
        return this.pos.offset(direction, distance);
    }

    public BlockPos offset(Direction.Axis axis, int distance) {
        return this.pos.offset(axis, distance);
    }

    public BlockPos up() {
        return this.offset(Direction.UP);
    }

    public BlockPos up(int distance) {
        return this.offset(Direction.UP, distance);
    }

    public BlockPos down() {
        return this.offset(Direction.DOWN);
    }

    public BlockPos down(int i) {
        return this.offset(Direction.DOWN, i);
    }

    public BlockPos north() {
        return this.offset(Direction.NORTH);
    }

    public BlockPos north(int distance) {
        return this.offset(Direction.NORTH, distance);
    }

    public BlockPos south() {
        return this.offset(Direction.SOUTH);
    }

    public BlockPos south(int distance) {
        return this.offset(Direction.SOUTH, distance);
    }

    public BlockPos west() {
        return this.offset(Direction.WEST);
    }

    public BlockPos west(int distance) {
        return this.offset(Direction.WEST, distance);
    }

    public BlockPos east() {
        return this.offset(Direction.EAST);
    }

    public BlockPos east(int distance) {
        return this.offset(Direction.EAST, distance);
    }

    public boolean equalsPos(BlockPos blockPos) {
        return this.pos.equals(blockPos);
    }

    public boolean canInteractWithBlockAt(double additionalRange) {
        return BedrockMiner.player.canInteractWithBlockAt(this.pos, additionalRange);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TaskBlock taskBlock = (TaskBlock) o;
        return Objects.equals(world, taskBlock.world) && Objects.equals(pos, taskBlock.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, pos);
    }
}
