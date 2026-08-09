package com.github.bunnyi116.bedrockminer.config;

import com.github.bunnyi116.bedrockminer.task.TaskRegion;
import com.github.bunnyi116.bedrockminer.util.BlockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class Config {
    public boolean disableEmptyHandSwitchToggle = false;
    public boolean disable = false;
    public boolean debug = false;
    public boolean fast = true;
    public boolean simpleMode = false;
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
        var list = new ArrayList<String>();
        list.add(BlockUtils.getKeyString(Blocks.BEDROCK));                  // 基岩
        // 防误触，取消以下默认白名单
        // list.addTask(BlockUtils.getId(Blocks.END_PORTAL));               // 末地传送门
        // list.addTask(BlockUtils.getId(Blocks.END_PORTAL_FRAME));         // 末地传送门-框架
        // list.addTask(BlockUtils.getId(Blocks.END_GATEWAY));              // 末地折跃门
        return list;
    }

    public static List<String> getDefaultBlockBlacklistServer() {
        // 默认方块黑名单 (用于限制的服务器, 与自定义黑名单分离)
        var list = new ArrayList<String>();
        list.add(BlockUtils.getKeyString(Blocks.BARRIER));                    // 屏障
        list.add(BlockUtils.getKeyString(Blocks.COMMAND_BLOCK));              // 普通命令方块
        list.add(BlockUtils.getKeyString(Blocks.CHAIN_COMMAND_BLOCK));        // 连锁型命令方块
        list.add(BlockUtils.getKeyString(Blocks.REPEATING_COMMAND_BLOCK));    // 循环型命令方块
        list.add(BlockUtils.getKeyString(Blocks.STRUCTURE_VOID));             // 结构空位
        list.add(BlockUtils.getKeyString(Blocks.STRUCTURE_BLOCK));            // 结构方块
        list.add(BlockUtils.getKeyString(Blocks.JIGSAW));                     // 拼图方块
        return list;
    }


    public boolean isAllowBlock(Block block) {
        var mc = Minecraft.getInstance();
        // 方块黑名单检查(服务器)
        if (!mc.isLocalServer()) {
            for (var defaultBlockBlack : blockBlacklistServer) {
                if (BlockUtils.getKeyString(block).equals(defaultBlockBlack)) {
                    return false;
                }
            }
        }
        // 方块白名单检查(用户自定义)
        for (var blockBlack : blockWhitelist) {
            if (BlockUtils.getKeyString(block).equals(blockBlack)) {
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
