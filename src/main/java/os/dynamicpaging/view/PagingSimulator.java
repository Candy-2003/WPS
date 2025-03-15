package os.dynamicpaging.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PagingSimulator extends JFrame {
    //============= 系统配置 =============//
    private static final int TOTAL_MEMORY = 64 * 1024;
    private int allocatedBlocks = 4;
    private SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private int historyCounter = 1;

    //============= 核心数据结构 =============//
    static class PageTableEntry {
        boolean present;
        int frame;
        boolean modified;
        int diskLocation;

        public PageTableEntry(boolean present, int frame, boolean modified, int diskLocation) {
            this.present = present;
            this.frame = frame;
            this.modified = modified;
            this.diskLocation = diskLocation;
        }
    }

    static class MemoryBlock {
        Integer page;
        long loadTime;
        boolean modified;

        public MemoryBlock(Integer page, long loadTime) {
            this.page = page;
            this.loadTime = loadTime;
            this.modified = false;
        }
    }

    //============= GUI组件 =============//
    private DefaultTableModel pageTableModel;
    private DefaultTableModel memoryModel;
    private DefaultTableModel historyModel;
    private JTextField blockCountField;
    private JTextField pageField;
    private JTextField offsetField;
    private JComboBox<String> opCombo;
    private JTextArea logArea;

    //============= 核心数据 =============//
    private PageTableEntry[] pageTable = new PageTableEntry[64];
    private MemoryBlock[] physicalMemory;

    public PagingSimulator() {
        initSystem();
        initComponents();
    }

    //============= 初始化方法 =============//
    private void initSystem() {
        int currentDiskLoc = 100;
        Random rand = new Random();
        for (int i = 0; i < 64; i++) {
            pageTable[i] = new PageTableEntry(false, -1, false, currentDiskLoc);
            currentDiskLoc += 1 + rand.nextInt(20);
        }
        physicalMemory = new MemoryBlock[allocatedBlocks];
        Arrays.fill(physicalMemory, null);
    }

    //============= GUI初始化 =============//
    private void initComponents() {
        setTitle("动态分页存储管理模拟器");
        setSize(1600, 800);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel();
        blockCountField = new JTextField("4", 4);
        JButton initBtn = new JButton("应用配置");
        opCombo = new JComboBox<>(new String[]{"save", "load", "+", "-", "*", "/"});
        pageField = new JTextField(5);
        offsetField = new JTextField(5);
        JButton execButton = new JButton("执行指令");

        controlPanel.add(new JLabel("内存块数(≥4):"));
        controlPanel.add(blockCountField);
        controlPanel.add(initBtn);
        controlPanel.add(new JLabel("操作类型:"));
        controlPanel.add(opCombo);
        controlPanel.add(new JLabel("页号(0-63):"));
        controlPanel.add(pageField);
        controlPanel.add(new JLabel("页内地址:"));
        controlPanel.add(offsetField);
        controlPanel.add(execButton);
        add(controlPanel, BorderLayout.NORTH);

        JPanel mainPanel = new JPanel(new GridLayout(1, 3));

        // 页表
        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "内存块号", "修改", "磁盘位置"}, 0);
        JTable pageTable = new JTable(pageTableModel);
        mainPanel.add(new JScrollPane(pageTable));

        // 内存状态
        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "加载时间", "修改"}, 0);
        JTable memoryTable = new JTable(memoryModel);
        mainPanel.add(new JScrollPane(memoryTable));

        // 历史记录（新增操作列）
        historyModel = new DefaultTableModel(new Object[]{"序号", "时间", "操作", "页号", "物理地址", "结果"}, 0);
        JTable historyTable = new JTable(historyModel);
        mainPanel.add(new JScrollPane(historyTable));

        add(mainPanel, BorderLayout.CENTER);

        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        initBtn.addActionListener(e -> applyConfig());
        execButton.addActionListener(e -> executeInstruction());
        refreshAll();
    }

    //============= 配置应用 =============//
    private void applyConfig() {
        try {
            int newCount = Integer.parseInt(blockCountField.getText());
            if (newCount < 4) {
                JOptionPane.showMessageDialog(this, "内存块数不能小于4!");
                return;
            }
            allocatedBlocks = newCount;
            initSystem();

            // 清空历史记录
            historyModel.setRowCount(0);
            historyCounter = 1;

            refreshAll();
            logArea.append("系统已重置：内存块数=" + allocatedBlocks + "\n");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "请输入有效数字!");
        }
    }

    //============= 核心逻辑 =============//
    private void executeInstruction() {
        try {
            String operation = (String) opCombo.getSelectedItem();
            int page = Integer.parseInt(pageField.getText());
            int offset = Integer.parseInt(offsetField.getText());
            int physicalAddr = -1;
            String result = "";

            if (invalidAddress(page, offset)) {
                logArea.append("错误: 无效地址!\n");
                addHistoryRecord(operation, page, physicalAddr, "无效地址");
                return;
            }

            PageTableEntry entry = pageTable[page];
            if (entry.present) {
                physicalAddr = entry.frame * 1024 + offset;
                result = "不缺页";
            } else {
                result = handlePageFault(page);
                if (pageTable[page].present) {
                    physicalAddr = pageTable[page].frame * 1024 + offset;
                }
            }

            // 处理写操作（仅对save操作设置修改标志）
            if ("save".equals(operation) && entry.present) {
                entry.modified = true;
                physicalMemory[entry.frame].modified = true;
            }

            addHistoryRecord(operation, page, physicalAddr, result);
            refreshAll();

        } catch (NumberFormatException ex) {
            logArea.append("错误: 输入格式错误!\n");
        }
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
        MemoryBlock victimBlock = physicalMemory[victimFrame];
        int victimPage = victimBlock.page;

        // 仅当页面被修改时才写回磁盘
        if (victimBlock.modified) {
            logArea.append("  写回页" + victimPage + "到磁盘位置"
                    + pageTable[victimPage].diskLocation + "\n");
            pageTable[victimPage].modified = false; // 写回后重置修改标志
        }

        replacePage(victimFrame, page);
        return "淘汰第" + victimPage + "页";
    }
    private int findFreeFrame() {
        for (int i = 0; i < allocatedBlocks; i++) {
            if (physicalMemory[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private int findVictimFrame() {
        int oldest = 0;
        long minTime = Long.MAX_VALUE;
        for (int i = 0; i < allocatedBlocks; i++) {
            if (physicalMemory[i] != null && physicalMemory[i].loadTime < minTime) {
                minTime = physicalMemory[i].loadTime;
                oldest = i;
            }
        }
        return oldest;
    }

    private void loadPage(int page, int frame) {
        // 写回旧页
        MemoryBlock old = physicalMemory[frame];
        if (old != null && old.modified) {
            logArea.append("  写回页" + old.page + "到磁盘位置"
                    + pageTable[old.page].diskLocation + "\n");
            pageTable[old.page].modified = false;
        }

        // 加载新页
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

    //============= 辅助方法 =============//
    private boolean invalidAddress(int page, int offset) {
        return (page < 0) || (page >= 64) || (offset < 0) || (offset >= 1024);
    }

    //============= 界面刷新 =============//
    private void refreshPageTable() {
        pageTableModel.setRowCount(0);
        for (int i = 0; i < 64; i++) {
            PageTableEntry e = pageTable[i];
            pageTableModel.addRow(new Object[]{
                    i,
                    e.present ? "true" : "false",
                    e.present ? e.frame : "N/A",  // 新增内存块号显示
                    e.modified ? "true" : "false",
                    e.diskLocation
            });
        }
    }

    private void refreshMemoryTable() {
        memoryModel.setRowCount(0);
        for (int i = 0; i < allocatedBlocks; i++) {
            MemoryBlock b = physicalMemory[i];
            memoryModel.addRow(new Object[]{
                    i,
                    (b != null) ? b.page : "空",
                    (b != null) ? TIME_FORMAT.format(new Date(b.loadTime)) : "N/A",
                    (b != null) ? b.modified : false
            });
        }
    }


    // ... [其他分页管理方法保持不变] ...

    //============= 历史记录 =============//
    private void addHistoryRecord(String operation, int page, int addr, String result) {
        historyModel.addRow(new Object[]{
                historyCounter++,
                TIME_FORMAT.format(new Date()),
                operation,  // 新增操作列
                page,
                (addr != -1) ? addr : "N/A",
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
//package os.dynamicpaging.view;
//
//import javax.swing.*;
//import javax.swing.table.DefaultTableModel;
//import java.awt.*;
//import java.text.SimpleDateFormat;
//import java.util.*;
//
//public class PagingSimulator extends JFrame {
//    //============= 系统配置 =============//
//    private static final int TOTAL_MEMORY = 64 * 1024;  // 总内存64KB
//    private int allocatedBlocks = 4;                   // 分配的内存块数
//    private SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
//    private int historyCounter = 1;
//
//    //============= 核心数据结构 =============//
//    static class PageTableEntry {
//        boolean present;
//        int frame;
//        boolean modified;
//        int diskLocation;
//
//        public PageTableEntry(boolean present, int frame, boolean modified, int diskLocation) {
//            this.present = present;
//            this.frame = frame;
//            this.modified = modified;
//            this.diskLocation = diskLocation;
//        }
//    }
//
//    static class MemoryBlock {
//        Integer page;
//        long loadTime;
//        boolean modified;
//
//        public MemoryBlock(Integer page, long loadTime) {
//            this.page = page;
//            this.loadTime = loadTime;
//            this.modified = false;
//        }
//    }
//
//    //============= GUI组件 =============//
//    private DefaultTableModel pageTableModel;
//    private DefaultTableModel memoryModel;
//    private DefaultTableModel historyModel;
//    private JTextField blockCountField;
//    private JTextField pageField;
//    private JTextField offsetField;
//    private JComboBox<String> opCombo;
//    private JTextArea logArea;
//
//    //============= 核心数据 =============//
//    private PageTableEntry[] pageTable = new PageTableEntry[64];
//    private MemoryBlock[] physicalMemory;
//
//    public PagingSimulator() {
//        initSystem();
//        initComponents();
//    }
//
//    //============= 初始化方法 =============//
//    private void initSystem() {
//        // 初始化页表（递增随机磁盘位置）
//        int currentDiskLoc = 100;
//        Random rand = new Random();
//        for (int i = 0; i < 64; i++) {
//            pageTable[i] = new PageTableEntry(false, -1, false, currentDiskLoc);
//            currentDiskLoc += 1 + rand.nextInt(20);
//        }
//
//        // 初始化物理内存
//        physicalMemory = new MemoryBlock[allocatedBlocks];
//        Arrays.fill(physicalMemory, null);
//    }
//
//    //============= GUI初始化 =============//
//    private void initComponents() {
//        setTitle("动态分页存储管理模拟器");
//        setSize(1600, 800);
//        setDefaultCloseOperation(EXIT_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        //----- 控制面板 -----
//        JPanel controlPanel = new JPanel();
//        blockCountField = new JTextField("4", 4);
//        JButton initBtn = new JButton("应用配置");
//        opCombo = new JComboBox<>(new String[]{"save", "load", "+", "-", "*", "/"});
//        pageField = new JTextField(5);
//        offsetField = new JTextField(5);
//        JButton execButton = new JButton("执行指令");
//
//        controlPanel.add(new JLabel("内存块数(≥4):"));
//        controlPanel.add(blockCountField);
//        controlPanel.add(initBtn);
//        controlPanel.add(new JLabel("操作类型:"));
//        controlPanel.add(opCombo);
//        controlPanel.add(new JLabel("页号(0-63):"));
//        controlPanel.add(pageField);
//        controlPanel.add(new JLabel("页内地址:"));
//        controlPanel.add(offsetField);
//        controlPanel.add(execButton);
//        add(controlPanel, BorderLayout.NORTH);
//
//        //----- 主显示面板 -----
//        JPanel mainPanel = new JPanel(new GridLayout(1, 3));
//
//        // 页表（新增内存块号列）
//        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "内存块号", "修改", "磁盘位置"}, 0);
//        JTable pageTable = new JTable(pageTableModel);
//        mainPanel.add(new JScrollPane(pageTable));
//
//        // 内存状态
//        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "加载时间", "修改"}, 0);
//        JTable memoryTable = new JTable(memoryModel);
//        mainPanel.add(new JScrollPane(memoryTable));
//
//        // 历史记录
//        historyModel = new DefaultTableModel(new Object[]{"序号", "时间", "页号", "物理地址", "结果"}, 0);
//        JTable historyTable = new JTable(historyModel);
//        mainPanel.add(new JScrollPane(historyTable));
//
//        add(mainPanel, BorderLayout.CENTER);
//
//        //----- 日志区域 -----
//        logArea = new JTextArea();
//        logArea.setEditable(false);
//        add(new JScrollPane(logArea), BorderLayout.SOUTH);
//
//        // 事件监听
//        initBtn.addActionListener(e -> applyConfig());
//        execButton.addActionListener(e -> executeInstruction());
//        refreshAll();
//    }
//
//    //============= 配置应用 =============//
//    private void applyConfig() {
//        try {
//            int newCount = Integer.parseInt(blockCountField.getText());
//            if (newCount < 4) {
//                JOptionPane.showMessageDialog(this, "内存块数不能小于4!");
//                return;
//            }
//            allocatedBlocks = newCount;
//            initSystem();
//            refreshAll();
//            logArea.append("系统已重置：内存块数=" + allocatedBlocks + "\n");
//        } catch (NumberFormatException ex) {
//            JOptionPane.showMessageDialog(this, "请输入有效数字!");
//        }
//    }
//
//    //============= 核心逻辑 =============//
//    private void executeInstruction() {
//        try {
//            String operation = (String) opCombo.getSelectedItem();
//            int page = Integer.parseInt(pageField.getText());
//            int offset = Integer.parseInt(offsetField.getText());
//            int physicalAddr = -1;
//            String result = "";
//
//            // 地址验证
//            if (invalidAddress(page, offset)) {
//                logArea.append("错误: 无效地址!\n");
//                addHistoryRecord(page, physicalAddr, "无效地址");
//                return;
//            }
//
//            PageTableEntry entry = pageTable[page];
//            if (entry.present) {
//                physicalAddr = entry.frame * 1024 + offset;
//                result = "不缺页";
//            } else {
//                result = handlePageFault(page);
//                if (pageTable[page].present) {
//                    physicalAddr = pageTable[page].frame * 1024 + offset;
//                }
//            }
//
//            // 处理写操作
//            if (operation.equals("save") && entry.present) {
//                entry.modified = true;
//                physicalMemory[entry.frame].modified = true;
//            }
//
//            addHistoryRecord(page, physicalAddr, result);
//            refreshAll();
//
//        } catch (NumberFormatException ex) {
//            logArea.append("错误: 输入格式错误!\n");
//        }
//    }
//
//    //============= 分页管理 =============//
//    private String handlePageFault(int page) {
//        logArea.append(">>> 处理缺页: 页" + page + "\n");
//
//        int freeFrame = findFreeFrame();
//        if (freeFrame != -1) {
//            loadPage(page, freeFrame);
//            return "缺页";
//        }
//
//        int victimFrame = findVictimFrame();
//        int victimPage = physicalMemory[victimFrame].page;
//        replacePage(victimFrame, page);
//        return "淘汰第" + victimPage + "页";
//    }
//
//    private int findFreeFrame() {
//        for (int i = 0; i < allocatedBlocks; i++) {
//            if (physicalMemory[i] == null) {
//                return i;
//            }
//        }
//        return -1;
//    }
//
//    private int findVictimFrame() {
//        int oldest = 0;
//        long minTime = Long.MAX_VALUE;
//        for (int i = 0; i < allocatedBlocks; i++) {
//            if (physicalMemory[i] != null && physicalMemory[i].loadTime < minTime) {
//                minTime = physicalMemory[i].loadTime;
//                oldest = i;
//            }
//        }
//        return oldest;
//    }
//
//    private void loadPage(int page, int frame) {
//        // 写回旧页
//        MemoryBlock old = physicalMemory[frame];
//        if (old != null && old.modified) {
//            logArea.append("  写回页" + old.page + "到磁盘位置"
//                    + pageTable[old.page].diskLocation + "\n");
//            pageTable[old.page].modified = false;
//        }
//
//        // 加载新页
//        physicalMemory[frame] = new MemoryBlock(page, System.currentTimeMillis());
//        pageTable[page].present = true;
//        pageTable[page].frame = frame;
//        logArea.append("  加载页" + page + "到内存块" + frame + "\n");
//    }
//
//    private void replacePage(int victimFrame, int newPage) {
//        int oldPage = physicalMemory[victimFrame].page;
//        pageTable[oldPage].present = false;
//        loadPage(newPage, victimFrame);
//    }
//
//    //============= 辅助方法 =============//
//    private boolean invalidAddress(int page, int offset) {
//        return (page < 0) || (page >= 64) || (offset < 0) || (offset >= 1024);
//    }
//
//    //============= 界面刷新 =============//
//    private void refreshPageTable() {
//        pageTableModel.setRowCount(0);
//        for (int i = 0; i < 64; i++) {
//            PageTableEntry e = pageTable[i];
//            pageTableModel.addRow(new Object[]{
//                    i,
//                    e.present ? "true" : "false",
//                    e.present ? e.frame : "N/A",  // 新增内存块号显示
//                    e.modified ? "true" : "false",
//                    e.diskLocation
//            });
//        }
//    }
//
//    private void refreshMemoryTable() {
//        memoryModel.setRowCount(0);
//        for (int i = 0; i < allocatedBlocks; i++) {
//            MemoryBlock b = physicalMemory[i];
//            memoryModel.addRow(new Object[]{
//                    i,
//                    (b != null) ? b.page : "空",
//                    (b != null) ? TIME_FORMAT.format(new Date(b.loadTime)) : "N/A",
//                    (b != null) ? b.modified : false
//            });
//        }
//    }
//
//    private void addHistoryRecord(int page, int addr, String result) {
//        historyModel.addRow(new Object[]{
//                historyCounter++,
//                TIME_FORMAT.format(new Date()),
//                page,
//                (addr != -1) ? addr : "N/A",
//                result
//        });
//    }
//
//    private void refreshAll() {
//        refreshPageTable();
//        refreshMemoryTable();
//        repaint();
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> {
//            PagingSimulator frame = new PagingSimulator();
//            frame.setVisible(true);
//        });
//    }
//}