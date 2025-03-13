package os.dynamicpaging.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;

public class PagingSimulator extends JFrame {
    // 内存配置常量
    private static final int MEMORY_SIZE = 64 * 1024;   // 64KB
    private static final int BLOCK_SIZE = 1024;         // 1KB/块
    private static final int BLOCK_COUNT = MEMORY_SIZE / BLOCK_SIZE;

    // 页表项数据结构
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

    // 内存块数据结构
    static class MemoryBlock {
        Integer page;       // 存放的页号
        long loadTime;      // 加载时间（用于FIFO）
        boolean modified;   // 是否被修改

        public MemoryBlock(Integer page, long loadTime) {
            this.page = page;
            this.loadTime = loadTime;
            this.modified = false;
        }
    }

    // GUI组件
    private DefaultTableModel pageTableModel;
    private DefaultTableModel memoryModel;
    private JTextArea logArea;
    private JTextField pageField;
    private JTextField offsetField;
    private JComboBox<String> opCombo;

    // 核心数据结构
    private PageTableEntry[] pageTable = new PageTableEntry[64]; // 64页（6位页号）
    private MemoryBlock[] physicalMemory = new MemoryBlock[BLOCK_COUNT];
    private int allocatedBlocks = 4;  // 分配给作业的内存块数

    public PagingSimulator() {
        initPageTable();
        initComponents();
    }

    // 初始化页表（示例数据）
    private void initPageTable() {
        pageTable[0] = new PageTableEntry(true, 5, false, 10);
        pageTable[1] = new PageTableEntry(true, 8, false, 12);
        pageTable[2] = new PageTableEntry(true, 9, false, 13);
        pageTable[3] = new PageTableEntry(true, 1, false, 21);
        for (int i = 4; i < 64; i++) {
            pageTable[i] = new PageTableEntry(false, -1, false, 100 + i);
        }
    }

    // 初始化界面组件
    private void initComponents() {
        setTitle("动态分页存储管理模拟器");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 控制面板
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

        // 页表显示
        JPanel tablePanel = new JPanel(new GridLayout(1, 2));
        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "内存块", "修改", "磁盘位置"}, 0);
        JTable pageTable = new JTable(pageTableModel);
        refreshPageTable();
        tablePanel.add(new JScrollPane(pageTable));

        // 内存状态显示
        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "加载时间", "修改"}, 0);
        JTable memoryTable = new JTable(memoryModel);
        refreshMemoryTable();
        tablePanel.add(new JScrollPane(memoryTable));
        add(tablePanel, BorderLayout.CENTER);

        // 日志区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // 事件处理
        execButton.addActionListener(e -> executeInstruction());
    }

    // 执行指令
    private void executeInstruction() {
        try {
            int page = Integer.parseInt(pageField.getText());
            int offset = Integer.parseInt(offsetField.getText());
            boolean isWrite = "写入".equals(opCombo.getSelectedItem());

            // 检查地址有效性
            if (page < 0 || page >= 64 || offset < 0 || offset >= 1024) {
                logArea.append("错误: 无效的页号或偏移地址!\n");
                return;
            }

            // 地址转换
            PageTableEntry entry = pageTable[page];
            if (!entry.present) {
                handlePageFault(page); // 缺页处理
                entry = pageTable[page]; // 重新获取条目
            }

            // 更新修改位
            if (isWrite) {
                entry.modified = true;
                physicalMemory[entry.frame].modified = true;
            }

            // 计算物理地址
            int physicalAddr = (entry.frame << 10) | offset;
            logArea.append(String.format("逻辑地址: 页号=%d, 偏移=%d → 物理地址: %d (块号=%d)\n",
                    page, offset, physicalAddr, entry.frame));

            refreshAll();
        } catch (NumberFormatException ex) {
            logArea.append("错误: 请输入有效的数字!\n");
        }
    }

    // 处理缺页中断
    private void handlePageFault(int page) {
        logArea.append(">>> 发生缺页中断，正在处理页号: " + page + "...\n");

        // 查找空闲块
        int freeFrame = findFreeFrame();
        if (freeFrame != -1) {
            loadPage(page, freeFrame);
            return;
        }

        // 使用FIFO置换
        int victimFrame = findVictimFrame();
        replacePage(victimFrame, page);
    }

    // 查找空闲内存块（在分配的块内查找）
    private int findFreeFrame() {
        for (int i = 0; i < allocatedBlocks; i++) {
            if (physicalMemory[i] == null || physicalMemory[i].page == null) {
                return i;
            }
        }
        return -1;
    }

    // FIFO置换算法找被淘汰的页
    private int findVictimFrame() {
        int oldestIndex = 0;
        long oldestTime = Long.MAX_VALUE;
        for (int i = 0; i < allocatedBlocks; i++) {
            if (physicalMemory[i].loadTime < oldestTime) {
                oldestTime = physicalMemory[i].loadTime;
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    // 加载页面到内存
    private void loadPage(int page, int frame) {
        // 写回旧页（如果被修改）
        if (physicalMemory[frame] != null && physicalMemory[frame].modified) {
            logArea.append("  写回页" + physicalMemory[frame].page + "到磁盘\n");
        }

        // 加载新页
        physicalMemory[frame] = new MemoryBlock(page, System.currentTimeMillis());
        pageTable[page].present = true;
        pageTable[page].frame = frame;
        logArea.append("  加载页" + page + "到内存块" + frame + "\n");
    }

    // 替换页面
    private void replacePage(int victimFrame, int newPage) {
        int oldPage = physicalMemory[victimFrame].page;
        pageTable[oldPage].present = false; // 标记旧页不在内存

        loadPage(newPage, victimFrame);
        logArea.append("  置换: 淘汰页" + oldPage + " (块" + victimFrame + ")\n");
    }

    // 刷新表格数据
    private void refreshPageTable() {
        pageTableModel.setRowCount(0);
        for (int i = 0; i < pageTable.length; i++) {
            PageTableEntry entry = pageTable[i];
            pageTableModel.addRow(new Object[]{
                    i,
                    entry.present ? "√" : "×",
                    entry.present ? entry.frame : "",
                    entry.modified ? "√" : "×",
                    entry.diskLocation
            });
        }
    }

    private void refreshMemoryTable() {
        memoryModel.setRowCount(0);
        for (int i = 0; i < allocatedBlocks; i++) {
            MemoryBlock block = physicalMemory[i];
            if (block != null) {
                memoryModel.addRow(new Object[]{
                        i,
                        block.page,
                        new Date(block.loadTime),
                        block.modified ? "√" : "×"
                });
            }
        }
    }

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