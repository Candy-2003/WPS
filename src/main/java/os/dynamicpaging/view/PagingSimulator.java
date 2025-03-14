package os.dynamicpaging.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PagingSimulator extends JFrame {
    //============= 系统配置常量 =============//
    private static final int MEMORY_SIZE = 64 * 1024;    // 总内存64KB
    private static final int BLOCK_SIZE = 1024;          // 每个内存块1KB
    private static final int BLOCK_COUNT = MEMORY_SIZE / BLOCK_SIZE; // 总内存块数64
    private static final int ALLOCATED_BLOCKS = 4;       // 分配给作业的内存块数量
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    //============= 核心数据结构 =============//
    static class PageTableEntry {
        boolean present;    // 页面存在标志
        int frame;          // 内存块号（仅在present为true时有效）
        boolean modified;   // 修改标志
        int diskLocation;   // 磁盘存储位置

        public PageTableEntry(boolean present, int frame, boolean modified, int diskLocation) {
            this.present = present;
            this.frame = frame;
            this.modified = modified;
            this.diskLocation = diskLocation;
        }
    }

    static class MemoryBlock {
        Integer page;       // 存储的页号
        long loadTime;      // 加载时间戳
        boolean modified;   // 修改标志

        public MemoryBlock(Integer page, long loadTime) {
            this.page = page;
            this.loadTime = loadTime;
            this.modified = false;
        }
    }

    //============= GUI组件 =============//
    private DefaultTableModel pageTableModel;  // 页表模型（移除内存块列）
    private DefaultTableModel memoryModel;     // 内存状态模型（新增物理地址范围）
    private DefaultTableModel historyModel;    // 历史记录模型（新增物理地址列）
    private JTextField pageField;              // 页号输入
    private JTextField offsetField;            // 页内地址输入
    private JComboBox<String> opCombo;         // 操作类型选择
    private JTextArea logArea;                 // 日志区域

    //============= 核心数据 =============//
    private PageTableEntry[] pageTable = new PageTableEntry[64];      // 页表（64个条目）
    private MemoryBlock[] physicalMemory = new MemoryBlock[ALLOCATED_BLOCKS]; // 物理内存（4个块）

    //============= 初始化方法 =============//
    public PagingSimulator() {
        initPageTable();
        initComponents();
    }

    // 初始化页表数据
    private void initPageTable() {
        // 初始化所有页都不在内存中
        for (int i = 0; i < 64; i++) {
            pageTable[i] = new PageTableEntry(false, -1, false, 100 + i);
        }

        // 初始化内存块为空
        Arrays.fill(physicalMemory, null);
    }

    //============= GUI初始化 =============//
    private void initComponents() {
        setTitle("动态分页存储管理模拟器");
        setSize(1400, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        //----- 控制面板 -----
        JPanel controlPanel = new JPanel();
        opCombo = new JComboBox<>(new String[]{"读取", "写入"});
        pageField = new JTextField(5);
        offsetField = new JTextField(5);
        JButton execButton = new JButton("执行指令");

        controlPanel.add(new JLabel("操作类型:"));
        controlPanel.add(opCombo);
        controlPanel.add(new JLabel("页号(0-63):"));
        controlPanel.add(pageField);
        controlPanel.add(new JLabel("页内地址(0-1023):"));
        controlPanel.add(offsetField);
        controlPanel.add(execButton);
        add(controlPanel, BorderLayout.NORTH);

        //----- 主显示面板 -----
        JPanel mainPanel = new JPanel(new GridLayout(1, 3));

        // 页表显示（移除内存块列）
        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "修改", "磁盘位置"}, 0);
        JTable pageTable = new JTable(pageTableModel);
        mainPanel.add(new JScrollPane(pageTable));

        // 内存状态显示（新增物理地址范围）
        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "物理地址范围", "加载时间", "修改"}, 0);
        JTable memoryTable = new JTable(memoryModel);
        mainPanel.add(new JScrollPane(memoryTable));

        // 操作历史（新增物理地址列）
        historyModel = new DefaultTableModel(new Object[]{"时间", "页号", "物理地址", "结果"}, 0);
        JTable historyTable = new JTable(historyModel);
        mainPanel.add(new JScrollPane(historyTable));

        add(mainPanel, BorderLayout.CENTER);

        //----- 日志区域 -----
        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // 绑定执行按钮事件
        execButton.addActionListener(e -> executeInstruction());
        refreshAll();
    }

    //============= 核心逻辑 =============//
    private void executeInstruction() {
        try {
            int page = Integer.parseInt(pageField.getText());
            int offset = Integer.parseInt(offsetField.getText());
            boolean isWrite = "写入".equals(opCombo.getSelectedItem());
            int physicalAddr = -1;
            String result = "";

            // 地址有效性检查
            if (invalidAddress(page, offset)) {
                logArea.append("错误: 无效地址!\n");
                addHistoryRecord(page, physicalAddr, "无效地址");
                return;
            }

            PageTableEntry entry = pageTable[page];
            if (entry.present) {
                // 计算物理地址
                physicalAddr = calculatePhysicalAddress(entry.frame, offset);
                result = "不缺页";
            } else {
                // 处理缺页中断
                result = handlePageFault(page);
                entry = pageTable[page]; // 重新获取条目
                if (entry.present) {
                    physicalAddr = calculatePhysicalAddress(entry.frame, offset);
                }
            }

            // 处理写操作
            if (isWrite && entry.present) {
                entry.modified = true;
                physicalMemory[entry.frame].modified = true;
            }

            addHistoryRecord(page, physicalAddr, result);
            refreshAll();

        } catch (NumberFormatException ex) {
            logArea.append("错误: 请输入有效数字!\n");
        }
    }

    //============= 辅助方法 =============//
    // 地址有效性验证
    private boolean invalidAddress(int page, int offset) {
        return page < 0 || page >= 64 || offset < 0 || offset >= 1024;
    }

    // 物理地址计算
    private int calculatePhysicalAddress(int frame, int offset) {
        return frame * BLOCK_SIZE + offset;
    }

    //============= 分页管理 =============//
    // 处理缺页中断
    private String handlePageFault(int page) {
        logArea.append(">>> 处理缺页: 页" + page + "\n");

        int freeFrame = findFreeFrame();
        if (freeFrame != -1) {
            loadPage(page, freeFrame);
            return "缺页";
        }

        int victimFrame = findVictimFrame();
        int victimPage = physicalMemory[victimFrame].page;
        replacePage(victimFrame, page);
        return "淘汰第" + victimPage + "页";
    }

    // 查找空闲内存块
    private int findFreeFrame() {
        for (int i = 0; i < ALLOCATED_BLOCKS; i++) {
            if (physicalMemory[i] == null) {return i;}
        }
        return -1;
    }

    // FIFO算法寻找置换页
    private int findVictimFrame() {
        int oldestIndex = 0;
        long oldestTime = Long.MAX_VALUE;
        for (int i = 0; i < ALLOCATED_BLOCKS; i++) {
            if (physicalMemory[i] != null && physicalMemory[i].loadTime < oldestTime) {
                oldestTime = physicalMemory[i].loadTime;
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    // 加载页面到内存
    private void loadPage(int page, int frame) {
        // 如果原页面被修改过，写回磁盘
        MemoryBlock oldBlock = physicalMemory[frame];
        if (oldBlock != null && oldBlock.modified) {
            logArea.append("  写回页" + oldBlock.page + "到磁盘位置"
                    + pageTable[oldBlock.page].diskLocation + "\n");
            pageTable[oldBlock.page].modified = false;
        }

        // 加载新页面
        physicalMemory[frame] = new MemoryBlock(page, System.currentTimeMillis());
        pageTable[page].present = true;
        pageTable[page].frame = frame; // 关键：设置内存块号
        logArea.append("  加载页" + page + "到内存块" + frame + "\n");
    }

    // 执行页面置换
    private void replacePage(int victimFrame, int newPage) {
        int oldPage = physicalMemory[victimFrame].page;
        pageTable[oldPage].present = false; // 标记旧页不在内存
        loadPage(newPage, victimFrame);
    }

    //============= 界面刷新 =============//
    // 刷新页表显示（移除内存块列）
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

    // 刷新内存状态（新增物理地址范围）
    private void refreshMemoryTable() {
        memoryModel.setRowCount(0);
        for (int i = 0; i < ALLOCATED_BLOCKS; i++) {
            MemoryBlock block = physicalMemory[i];
            int startAddr = i * BLOCK_SIZE;
            int endAddr = (i + 1) * BLOCK_SIZE - 1;
            memoryModel.addRow(new Object[]{
                    i,
                    block != null ? block.page : "空",
                    startAddr + "-" + endAddr,
                    block != null ? TIME_FORMAT.format(new Date(block.loadTime)) : "N/A",
                    block != null ? (block.modified ? "√" : "×") : "×"
            });
        }
    }

    // 添加历史记录（新增物理地址列）
    private void addHistoryRecord(int page, int physicalAddr, String result) {
        historyModel.addRow(new Object[]{
                TIME_FORMAT.format(new Date()),
                page,
                physicalAddr != -1 ? physicalAddr : "N/A",
                result
        });
    }

    // 全局刷新
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
