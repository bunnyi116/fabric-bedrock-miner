package com.github.bunnyi116.bedrockminer.config;

import com.github.bunnyi116.bedrockminer.task.TaskRegion;
import com.github.bunnyi116.bedrockminer.util.block.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public boolean disable = false;
    public boolean debug = false;
    public boolean shortTsk = false;
    public List<Integer> floorsBlacklist = new ArrayList<>();
    public List<TaskRegion> ranges = new ArrayList<>();
    public List<String> blockWhitelist = getDefaultBlockWhitelist();
    public int limitMax = 1;

    public transient List<String> blockBlacklistServer = getDefaultBlockBlacklistServer();
    public transient Direction[] pistonDirections = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public transient Direction[] pistonFacings = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public transient Direction[] redstoneTorchDirections = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    public transient Direction[] redstoneTorchFacings = new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public static List<String> getDefaultBlockWhitelist() {
        ArrayList<String> list = new ArrayList<>();
        list.add(BlockUtils.getBlockId(Blocks.BEDROCK));                  // 基岩
        // 防误触，取消以下默认白名单
        // list.addTask(BlockUtils.getId(Blocks.END_PORTAL));               // 末地传送门
        // list.addTask(BlockUtils.getId(Blocks.END_PORTAL_FRAME));         // 末地传送门-框架
        // list.addTask(BlockUtils.getId(Blocks.END_GATEWAY));              // 末地折跃门
        return list;
    }

    public static List<String> getDefaultBlockBlacklistServer() {
        // 默认方块黑名单 (用于限制的服务器, 与自定义黑名单分离)
        ArrayList<String> list = new ArrayList<>();
        list.add(BlockUtils.getBlockId(Blocks.BARRIER));                    // 屏障
        list.add(BlockUtils.getBlockId(Blocks.COMMAND_BLOCK));              // 普通命令方块
        list.add(BlockUtils.getBlockId(Blocks.CHAIN_COMMAND_BLOCK));        // 连锁型命令方块
        list.add(BlockUtils.getBlockId(Blocks.REPEATING_COMMAND_BLOCK));    // 循环型命令方块
        list.add(BlockUtils.getBlockId(Blocks.STRUCTURE_VOID));             // 结构空位
        list.add(BlockUtils.getBlockId(Blocks.STRUCTURE_BLOCK));            // 结构方块
        list.add(BlockUtils.getBlockId(Blocks.JIGSAW));                     // 拼图方块
        return list;
    }


    public boolean isAllowBlock(Block block) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // 方块黑名单检查(服务器)
        if (!mc.isInSingleplayer()) {
            for (String defaultBlockBlack : blockBlacklistServer) {
                if (BlockUtils.getBlockId(block).equals(defaultBlockBlack)) {
                    return false;
                }
            }
        }
        // 方块白名单检查(用户自定义)
        for (String blockBlack : blockWhitelist) {
            if (BlockUtils.getBlockId(block).equals(blockBlack)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFloorsBlacklist(BlockPos pos) {
        if (!floorsBlacklist.isEmpty()) {  // 楼层限制
            return floorsBlacklist.contains(pos.getY());
        }
        return false;
    }

    public void save() {
        ConfigManager.getInstance().saveConfig();
    }


    public static Config getInstance() {
        return ConfigManager.getInstance().getConfig();
    }
}
