package os.dynamicpaging.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PagingSimulator extends JFrame {
    //============= 系统配置常量 =============//
    private static final int MEMORY_SIZE = 64 * 1024;   // 总内存64KB
    private static final int BLOCK_SIZE = 1024;         // 每个内存块1KB
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    //============= 核心数据结构 =============//
    static class PageTableEntry {
        boolean present;    // 存在标志
        int frame;          // 内存块号
        boolean modified;   // 修改标志
        int diskLocation;   // 磁盘位置

        public PageTableEntry(boolean present, int frame, boolean modified, int diskLocation) {
            this.present = present;
            this.frame = frame;
            this.modified = modified;
            this.diskLocation = diskLocation;
        }
    }

    static class MemoryBlock {
        Integer page;       // 存储的页号
        long loadTime;      // 加载时间
        boolean modified;   // 修改标志

        public MemoryBlock(Integer page, long loadTime) {
            this.page = page;
            this.loadTime = loadTime;
            this.modified = false;
        }
    }

    //============= GUI组件 =============//
    private DefaultTableModel pageTableModel;    // 页表模型
    private DefaultTableModel memoryModel;       // 内存状态模型
    private DefaultTableModel historyModel;      // 历史记录模型
    private JTextField pageField;                // 页号输入
    private JTextField offsetField;              // 偏移地址输入
    private JTextField blockCountField;          // 内存块数量输入
    private JTextArea logArea;                   // 日志区域

    //============= 核心数据 =============//
    private PageTableEntry[] pageTable = new PageTableEntry[64];  // 页表(64页)
    private ArrayList<MemoryBlock> physicalMemory;                // 物理内存块列表
    private int allocatedBlocks = 4;              // 当前分配的内存块数量

    //============= 初始化方法 =============//
    public PagingSimulator() {
        initComponents();
        initSystem(allocatedBlocks);  // 使用默认4个块初始化
    }

    /**
     * 初始化系统核心数据
     * @param blockCount 内存块数量
     */
    private void initSystem(int blockCount) {
        // 初始化页表
        for(int i=0; i<64; i++){
            pageTable[i] = new PageTableEntry(false, -1, false, 100+i);
        }

        // 初始化内存块
        physicalMemory = new ArrayList<>(blockCount);
        for(int i=0; i<blockCount; i++){
            physicalMemory.add(null); // 初始化为空
        }

        // 初始化预加载页（示例数据）
        loadPage(0, 0);
        loadPage(1, 1);
        loadPage(2, 2);
        loadPage(3, 3);
    }

    //============= GUI初始化 =============//
    private void initComponents() {
        setTitle("动态分页存储管理模拟器");
        setSize(1400, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        //----- 控制面板 -----
        JPanel controlPanel = new JPanel();
        blockCountField = new JTextField("4", 5);  // 内存块数量输入
        pageField = new JTextField(5);
        offsetField = new JTextField(5);
        JButton execButton = new JButton("执行指令");
        JButton initButton = new JButton("重新初始化");

        controlPanel.add(new JLabel("内存块数(>2):"));
        controlPanel.add(blockCountField);
        controlPanel.add(new JLabel("页号(0-63):"));
        controlPanel.add(pageField);
        controlPanel.add(new JLabel("页内地址(0-1023):"));
        controlPanel.add(offsetField);
        controlPanel.add(execButton);
        controlPanel.add(initButton);
        add(controlPanel, BorderLayout.NORTH);

        //----- 主显示面板 -----
        JPanel mainPanel = new JPanel(new GridLayout(1, 3));

        // 页表显示（移除内存块列）
        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "修改", "磁盘位置"}, 0);
        JTable pageTable = new JTable(pageTableModel);
        mainPanel.add(new JScrollPane(pageTable));

        // 内存状态表（添加物理地址列）
        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "物理地址范围", "加载时间"}, 0);
        JTable memoryTable = new JTable(memoryModel);
        mainPanel.add(new JScrollPane(memoryTable));

        // 历史记录表（添加物理地址列）
        historyModel = new DefaultTableModel(new Object[]{"时间", "页号", "物理地址", "结果"}, 0);
        JTable historyTable = new JTable(historyModel);
        mainPanel.add(new JScrollPane(historyTable));

        add(mainPanel, BorderLayout.CENTER);

        //----- 日志区域 -----
        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        //----- 事件监听 -----
        execButton.addActionListener(e -> executeInstruction());
        initButton.addActionListener(e -> reinitializeSystem());

        refreshAll();
    }

    //============= 核心逻辑 =============//
    /**
     * 执行内存访问指令
     */
    private void executeInstruction() {
        try {
            int page = Integer.parseInt(pageField.getText());
            int offset = Integer.parseInt(offsetField.getText());
            int physicalAddr = -1;
            String result = "";

            // 地址有效性检查
            if (page < 0 || page >= 64 || offset < 0 || offset >= 1024) {
                logArea.append("错误: 无效地址!\n");
                addHistoryRecord(page, physicalAddr, "无效地址");
                return;
            }

            PageTableEntry entry = pageTable[page];
            if (entry.present) {
                // 计算物理地址
                physicalAddr = entry.frame * BLOCK_SIZE + offset;
                result = "不缺页";
            } else {
                // 处理缺页中断
                result = handlePageFault(page);
                entry = pageTable[page]; // 重新获取条目
                if(entry.present) {
                    physicalAddr = entry.frame * BLOCK_SIZE + offset;
                }
            }

            // 记录历史
            addHistoryRecord(page, physicalAddr, result);
            refreshAll();

        } catch (NumberFormatException ex) {
            logArea.append("错误: 请输入有效数字!\n");
        }
    }

    /**
     * 重新初始化系统
     */
    private void reinitializeSystem() {
        try {
            int newBlockCount = Integer.parseInt(blockCountField.getText());
            if(newBlockCount <= 2) {
                JOptionPane.showMessageDialog(this, "内存块数必须大于2!");
                return;
            }
            allocatedBlocks = newBlockCount;
            initSystem(allocatedBlocks);
            refreshAll();
            logArea.append("系统已重新初始化，内存块数：" + allocatedBlocks + "\n");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "请输入有效数字!");
        }
    }

    //============= 分页管理方法 =============//
    /**
     * 处理缺页中断
     * @return 处理结果描述
     */
    private String handlePageFault(int page) {
        logArea.append(">>> 处理缺页: 页" + page + "\n");

        // 查找空闲块
        int freeFrame = findFreeFrame();
        if (freeFrame != -1) {
            loadPage(page, freeFrame);
            return "缺页";
        }

        // 执行页面置换
        int victimFrame = findVictimFrame();
        int victimPage = physicalMemory.get(victimFrame).page;
        replacePage(victimFrame, page);
        return "淘汰第" + victimPage + "页";
    }

    /**
     * 查找空闲内存块
     */
    private int findFreeFrame() {
        for (int i = 0; i < physicalMemory.size(); i++) {
            if (physicalMemory.get(i) == null) {return i;}
        }
        return -1;
    }

    /**
     * FIFO算法寻找置换页
     */
    private int findVictimFrame() {
        int oldestIndex = 0;
        long oldestTime = Long.MAX_VALUE;
        for (int i = 0; i < physicalMemory.size(); i++) {
            MemoryBlock block = physicalMemory.get(i);
            if (block != null && block.loadTime < oldestTime) {
                oldestTime = block.loadTime;
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    /**
     * 加载页面到内存
     */
    private void loadPage(int page, int frame) {
        // 如果原页面被修改过，写回磁盘
        MemoryBlock oldBlock = physicalMemory.get(frame);
        if (oldBlock != null && oldBlock.modified) {
            logArea.append("  写回页" + oldBlock.page + "到磁盘位置"
                    + pageTable[oldBlock.page].diskLocation + "\n");
            pageTable[oldBlock.page].modified = false;
        }

        // 加载新页面
        physicalMemory.set(frame, new MemoryBlock(page, System.currentTimeMillis()));
        pageTable[page].present = true;
        pageTable[page].frame = frame;
        logArea.append("  加载页" + page + "到内存块" + frame + "\n");
    }

    /**
     * 执行页面置换
     */
    private void replacePage(int victimFrame, int newPage) {
        // 标记旧页不在内存
        int oldPage = physicalMemory.get(victimFrame).page;
        pageTable[oldPage].present = false;

        // 加载新页
        loadPage(newPage, victimFrame);
    }

    //============= 界面刷新方法 =============//
    /**
     * 刷新页表显示
     */
    private void refreshPageTable() {
        pageTableModel.setRowCount(0);
        for (int i = 0; i < 64; i++) {
            PageTableEntry entry = pageTable[i];
            pageTableModel.addRow(new Object[]{
                    i,
                    entry.present ? "√" : "×",
                    entry.modified ? "√" : "×",
                    entry.diskLocation
            });
        }
    }

    /**
     * 刷新内存状态显示
     */
    private void refreshMemoryTable() {
        memoryModel.setRowCount(0);
        for (int i = 0; i < physicalMemory.size(); i++) {
            MemoryBlock block = physicalMemory.get(i);
            if(block != null) {
                int startAddr = i * BLOCK_SIZE;
                int endAddr = (i+1) * BLOCK_SIZE - 1;
                memoryModel.addRow(new Object[]{
                        i,
                        block.page,
                        startAddr + "-" + endAddr,
                        TIME_FORMAT.format(new Date(block.loadTime))
                });
            }
        }
    }

    /**
     * 添加历史记录
     */
    private void addHistoryRecord(int page, int physicalAddr, String result) {
        historyModel.addRow(new Object[]{
                TIME_FORMAT.format(new Date()),
                page,
                physicalAddr != -1 ? physicalAddr : "N/A",
                result
        });
    }

    /**
     * 全局刷新界面
     */
    private void refreshAll() {
        refreshPageTable();
        refreshMemoryTable();
        repaint();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PagingSimulator frame = new PagingSimulator();
            frame.setVisible(true);
        });
    }
}