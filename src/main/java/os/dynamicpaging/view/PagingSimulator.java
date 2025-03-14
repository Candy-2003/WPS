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
    private static final int ALLOCATED_BLOCKS = 4;       // 分配给作业的内存块数量
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private int historyCounter = 1;  // 新增：历史记录序号计数器

    //============= 核心数据结构 =============//
    static class PageTableEntry {
        boolean present;    // 页面存在标志
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
        boolean modified;  // 修改标志

        public MemoryBlock(Integer page, long loadTime) {
            this.page = page;
            this.loadTime = loadTime;
            this.modified = false;
        }
    }

    //============= GUI组件 =============//
    private DefaultTableModel pageTableModel;  // 页表模型
    private DefaultTableModel memoryModel;     // 内存状态模型
    private DefaultTableModel historyModel;    // 历史记录模型
    private JTextField pageField;              // 页号输入
    private JTextField offsetField;            // 页内地址输入
    private JComboBox<String> opCombo;         // 操作类型选择
    private JTextArea logArea;                 // 日志区域

    //============= 核心数据 =============//
    private PageTableEntry[] pageTable = new PageTableEntry[64];
    private MemoryBlock[] physicalMemory = new MemoryBlock[ALLOCATED_BLOCKS];

    //============= 初始化方法 =============//
    public PagingSimulator() {
        initPageTable();
        initComponents();
    }

    private void initPageTable() {
        // 初始化页表
        for (int i = 0; i < 64; i++) {
            pageTable[i] = new PageTableEntry(false, -1, false, 100 + i);
        }
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

        // 页表（使用true/false显示）
        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "修改", "磁盘位置"}, 0);
        JTable pageTable = new JTable(pageTableModel);
        mainPanel.add(new JScrollPane(pageTable));

        // 内存状态（移除物理地址范围列）
        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "加载时间", "修改"}, 0);
        JTable memoryTable = new JTable(memoryModel);
        mainPanel.add(new JScrollPane(memoryTable));

        // 历史记录（添加序号列）
        historyModel = new DefaultTableModel(new Object[]{"序号", "时间", "页号", "物理地址", "结果"}, 0);
        JTable historyTable = new JTable(historyModel);
        mainPanel.add(new JScrollPane(historyTable));

        add(mainPanel, BorderLayout.CENTER);

        //----- 日志区域 -----
        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

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

            if (invalidAddress(page, offset)) {
                logArea.append("错误: 无效地址!\n");
                addHistoryRecord(page, physicalAddr, "无效地址");
                return;
            }

            PageTableEntry entry = pageTable[page];
            if (entry.present) {
                physicalAddr = calculatePhysicalAddress(entry.frame, offset);
                result = "不缺页";
            } else {
                result = handlePageFault(page);
                entry = pageTable[page];
                if (entry.present) {
                    physicalAddr = calculatePhysicalAddress(entry.frame, offset);
                }
            }

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
    private boolean invalidAddress(int page, int offset) {
        return page < 0 || page >= 64 || offset < 0 || offset >= 1024;
    }

    private int calculatePhysicalAddress(int frame, int offset) {
        return frame * BLOCK_SIZE + offset;
    }

    //============= 分页管理 =============//
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

    private int findFreeFrame() {
        for (int i = 0; i < ALLOCATED_BLOCKS; i++) {
            if (physicalMemory[i] == null) {return i;}
        }
        return -1;
    }

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

    private void loadPage(int page, int frame) {
        if (physicalMemory[frame] != null && physicalMemory[frame].modified) {
            logArea.append("  写回页" + physicalMemory[frame].page + "到磁盘位置"
                    + pageTable[physicalMemory[frame].page].diskLocation + "\n");
            pageTable[physicalMemory[frame].page].modified = false;
        }

        physicalMemory[frame] = new MemoryBlock(page, System.currentTimeMillis());
        pageTable[page].present = true;
        pageTable[page].frame = frame;
        logArea.append("  加载页" + page + "到内存块" + frame + "\n");
    }

    private void replacePage(int victimFrame, int newPage) {
        int oldPage = physicalMemory[victimFrame].page;
        pageTable[oldPage].present = false;
        loadPage(newPage, victimFrame);
    }

    //============= 界面刷新 =============//
    private void refreshPageTable() {
        pageTableModel.setRowCount(0);
        for (int i = 0; i < 64; i++) {
            PageTableEntry entry = pageTable[i];
            pageTableModel.addRow(new Object[]{
                    i,
                    entry.present ? "true" : "false",  // 修改点1：使用字符串true/false
                    entry.modified ? "true" : "false", // 修改点1
                    entry.diskLocation
            });
        }
    }

    private void refreshMemoryTable() {
        memoryModel.setRowCount(0);
        for (int i = 0; i < ALLOCATED_BLOCKS; i++) {
            MemoryBlock block = physicalMemory[i];
            memoryModel.addRow(new Object[]{
                    i,
                    block != null ? block.page : "空",
                    block != null ? TIME_FORMAT.format(new Date(block.loadTime)) : "N/A",
                    block != null ? block.modified : false  // 修改点2：保持布尔值
            });
        }
    }

    private void addHistoryRecord(int page, int physicalAddr, String result) {
        historyModel.addRow(new Object[]{
                historyCounter++,  // 修改点3：添加自增序号
                TIME_FORMAT.format(new Date()),
                page,
                physicalAddr != -1 ? physicalAddr : "N/A",
                result
        });
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
