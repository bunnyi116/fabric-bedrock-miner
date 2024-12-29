package yan.lx.bedrockminer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import yan.lx.bedrockminer.Debug;
import yan.lx.bedrockminer.utils.BlockUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public static final File file = new File(FabricLoader.getInstance().getConfigDir().toFile(), "bedrockminer.json");
    public static final Config INSTANCE = Config.load();
    public boolean disable = false;
    public boolean debug = false;
    public boolean vertical = true;
    public boolean horizontal = true;
    public int taskLimit = 1;
    public List<Integer> floorsBlacklist = new ArrayList<>();
    public List<String> blockWhitelist = getDefaultBlockWhitelist();
    public List<String> blockBlacklist = new ArrayList<>();
    public transient List<String> blockBlacklistServer = getDefaultBlockBlacklistServer();

    public static List<String> getDefaultBlockWhitelist() {
        var list = new ArrayList<String>();
        list.add(BlockUtils.getBlockId(Blocks.BEDROCK));                  // 基岩
        // 防误触，取消以下默认白名单
        // list.add(BlockUtils.getId(Blocks.END_PORTAL));               // 末地传送门
        // list.add(BlockUtils.getId(Blocks.END_PORTAL_FRAME));         // 末地传送门-框架
        // list.add(BlockUtils.getId(Blocks.END_GATEWAY));              // 末地折跃门
        return list;
    }

    public static List<String> getDefaultBlockBlacklistServer() {
        // 默认方块黑名单 (用于限制的服务器, 与自定义黑名单分离)
        var list = new ArrayList<String>();
        list.add(BlockUtils.getBlockId(Blocks.BARRIER));                    // 屏障
        list.add(BlockUtils.getBlockId(Blocks.COMMAND_BLOCK));              // 普通命令方块
        list.add(BlockUtils.getBlockId(Blocks.CHAIN_COMMAND_BLOCK));        // 连锁型命令方块
        list.add(BlockUtils.getBlockId(Blocks.REPEATING_COMMAND_BLOCK));    // 循环型命令方块
        list.add(BlockUtils.getBlockId(Blocks.STRUCTURE_VOID));             // 结构空位
        list.add(BlockUtils.getBlockId(Blocks.STRUCTURE_BLOCK));            // 结构方块
        list.add(BlockUtils.getBlockId(Blocks.JIGSAW));                     // 拼图方块
        return list;
    }

    public static Config load() {
        Config config = null;
        Gson gson = new Gson();
        try (Reader reader = new FileReader(file)) {
            config = gson.fromJson(reader, Config.class);
            Debug.alwaysWrite("已成功加载配置文件");
        } catch (Exception e) {
            if (file.exists()) {
                if (file.delete()) {
                    Debug.alwaysWrite("无法加载配置,已成功删除配置文件");
                } else {
                    Debug.alwaysWrite("无法加载配置,删除配置文件失败");
                }
            } else {
                Debug.alwaysWrite("找不到配置文件");
            }
        }
        if (config == null) {
            Debug.alwaysWrite("使用默认配置");
            config = new Config();
            save();
        }
        return config;
    }

    public static void save() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(INSTANCE, writer);
        } catch (IOException e) {
            Debug.alwaysWrite("无法保存配置文件");
            e.printStackTrace();
        }
    }


}
