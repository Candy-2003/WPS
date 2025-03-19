package os.dynamicpaging.view;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PagingSimulator extends JFrame {
    //系统配置
    private static final int TOTAL_MEMORY = 64 * 1024;  // 总内存64KB
    private int allocatedBlocks = 4;                   // 当前分配的内存块数
    private SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");  // 时间格式
    private int historyCounter = 1;                    // 历史记录序号计数器
    private static final Font DEFAULT_FONT = new Font("微软雅黑", Font.PLAIN, 16);  // 全局字体

    /**
     * 页表项数据结构
     */
    static class PageTableEntry {
        boolean present;     // 页面存在标志（是否在内存中）
        int frame;           // 内存块号（当present为true时有效）
        boolean modified;    // 永久修改标志（记录页面是否被修改过）
        int diskLocation;    // 磁盘存储位置

        public PageTableEntry(boolean present, int frame, boolean modified, int diskLocation) {
            this.present = present;
            this.frame = frame;
            this.modified = modified;
            this.diskLocation = diskLocation;
        }
    }

    /**
     * 内存块数据结构
     */
    static class MemoryBlock {
        Integer page;        // 存储的页号
        long loadTime;       // 加载时间戳（用于FIFO算法）
        boolean modified;    // 当前修改状态（决定是否需要写回磁盘）

        public MemoryBlock(Integer page, long loadTime) {
            this.page = page;
            this.loadTime = loadTime;
            this.modified = false;
        }
    }

    //GUI组件
    private DefaultTableModel pageTableModel;    // 页表数据模型
    private DefaultTableModel memoryModel;       // 内存状态数据模型
    private DefaultTableModel historyModel;      // 历史记录数据模型
    private JTextField blockCountField;          // 内存块数输入框
    private JTextField pageField;                // 页号输入框
    private JTextField offsetField;              // 页内地址输入框
    private JComboBox<String> opCombo;           // 操作类型下拉框
    private JTextArea logArea;                   // 日志输出区域

    //核心数据
    private PageTableEntry[] pageTable = new PageTableEntry[64];  // 页表（64个页面）
    private MemoryBlock[] physicalMemory;        // 物理内存块数组

    public PagingSimulator() {
        initSystem();        // 初始化系统数据
        initComponents();    // 初始化界面组件
        setUIFont(DEFAULT_FONT);    // 设置全局字体
    }

    /**
     * 初始化系统核心数据
     */
    private void initSystem() {
        // 初始化页表项
        int currentDiskLoc = 100;
        Random rand = new Random();
        for (int i = 0; i < 64; i++) {
            pageTable[i] = new PageTableEntry(false, -1, false,
                    currentDiskLoc += 1 + rand.nextInt(20));
        }

        // 初始化物理内存数组
        physicalMemory = new MemoryBlock[allocatedBlocks];
        Arrays.fill(physicalMemory, null);
    }

    /**
     * 初始化界面组件
     */
    private void initComponents() {
        // 窗口基本设置
        setTitle("动态分页存储管理模拟器");
        setSize(1600, 850);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        //控制面板
        JPanel controlPanel = new JPanel();

        // 输入组件
        blockCountField = new JTextField("4", 5);
        JButton initBtn = new JButton("应用配置");
        opCombo = new JComboBox<>(new String[]{"save", "load", "+", "-", "*", "/"});
        pageField = new JTextField(6);       // 页号输入（0-63）
        offsetField = new JTextField(6);     // 页内地址输入（0-1023）
        JButton execButton = new JButton("执行指令");

        // 设置组件字体
        Font controlFont = new Font("微软雅黑", Font.PLAIN, 16);
        blockCountField.setFont(controlFont);
        initBtn.setFont(controlFont);
        opCombo.setFont(controlFont);
        pageField.setFont(controlFont);
        offsetField.setFont(controlFont);
        execButton.setFont(controlFont);

        // 添加组件到控制面板
        controlPanel.add(new JLabel(" 内存块数(4-64): "));
        controlPanel.add(blockCountField);
        controlPanel.add(initBtn);
        controlPanel.add(new JLabel(" 操作类型: "));
        controlPanel.add(opCombo);
        controlPanel.add(new JLabel(" 页号(0-63): "));
        controlPanel.add(pageField);
        controlPanel.add(new JLabel(" 页内地址: "));
        controlPanel.add(offsetField);
        controlPanel.add(execButton);

        add(controlPanel, BorderLayout.NORTH);

        //主显示面板
        JPanel mainPanel = new JPanel(new GridLayout(1, 3, 10, 0));

        // 左侧：页表显示
        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "内存块号", "修改", "磁盘位置"}, 0);
        JTable pageTable = new JTable(pageTableModel) {
            // 自定义单元格渲染（绿色背景）
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                boolean isPresent = (Boolean) getModel().getValueAt(row, 1);
                c.setBackground(isPresent ? new Color(200, 255, 200) : getBackground());
                return c;
            }
        };
        pageTable.setFont(DEFAULT_FONT);
        pageTable.getTableHeader().setFont(DEFAULT_FONT);
        pageTable.setRowHeight(30);
        JScrollPane leftScroll = new JScrollPane(pageTable);
        leftScroll.setBorder(BorderFactory.createTitledBorder("页表"));

        // 中间：内存状态
        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "加载时间"}, 0);
        JTable memoryTable = new JTable(memoryModel);
        memoryTable.setFont(DEFAULT_FONT);
        memoryTable.getTableHeader().setFont(DEFAULT_FONT);
        memoryTable.setRowHeight(30);
        JScrollPane centerScroll = new JScrollPane(memoryTable);
        centerScroll.setBorder(BorderFactory.createTitledBorder("内存状态"));

        // 右侧：操作历史
        historyModel = new DefaultTableModel(new Object[]{"序号", "时间", "操作", "页号", "物理地址", "结果"}, 0);
        JTable historyTable = new JTable(historyModel);
        historyTable.setFont(DEFAULT_FONT);
        historyTable.getTableHeader().setFont(DEFAULT_FONT);
        historyTable.setRowHeight(30);
        JScrollPane rightScroll = new JScrollPane(historyTable);
        rightScroll.setBorder(BorderFactory.createTitledBorder("操作历史"));

        mainPanel.add(leftScroll);
        mainPanel.add(centerScroll);
        mainPanel.add(rightScroll);
        add(mainPanel, BorderLayout.CENTER);

        // 日志区域
        logArea = new JTextArea(5, 20);
        logArea.setFont(DEFAULT_FONT);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);
//        logArea = new JTextArea();
//        logArea.setFont(DEFAULT_FONT);
//        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        //事件监听
        initBtn.addActionListener(e -> applyConfig());      // 配置应用事件
        execButton.addActionListener(e -> executeInstruction()); // 执行指令事件

        refreshAll();  // 初始界面刷新
    }

    /**
     * 应用配置（修改内存块数）
     */
    private void applyConfig() {
        try {
            int newCount = Integer.parseInt(blockCountField.getText());
            // 输入验证
            if (newCount < 4 || newCount > 64) {
                JOptionPane.showMessageDialog(this, "内存块数需在4-64之间!");
                return;
            }
            allocatedBlocks = newCount;
            initSystem();  // 重新初始化系统
            historyModel.setRowCount(0);  // 清空历史记录
            historyCounter = 1;
            refreshAll();
            logArea.append("系统已重置：内存块数=" + allocatedBlocks + "\n");
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "请输入有效数字!");
        }
    }

    /**
     * 执行内存访问指令
     */
    private void executeInstruction() {
        try {
            String operation = (String) opCombo.getSelectedItem();
            int page = Integer.parseInt(pageField.getText());
            int offset = Integer.parseInt(offsetField.getText());
            int physicalAddr = -1;
            String result = "";

            // 地址有效性检查
            if (invalidAddress(page, offset)) {
                logArea.append("错误: 无效地址!\n");
                addHistoryRecord(operation, page, physicalAddr, "无效地址");
                return;
            }

            PageTableEntry entry = pageTable[page];
            if (entry.present) {
                // 页面在内存中，直接计算物理地址
                physicalAddr = entry.frame * 1024 + offset;
                result = "不缺页";
            } else {
                // 触发缺页中断
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

            // 记录历史并刷新界面
            addHistoryRecord(operation, page, physicalAddr, result);
            refreshAll();

        } catch (NumberFormatException ex) {
            logArea.append("错误: 输入格式错误!\n");
        }
    }

    /**
     * 处理缺页中断
     * @param page 请求的页号
     * @return 处理结果描述
     */
    private String handlePageFault(int page) {
        logArea.append(">>> 处理缺页: 页" + page + "\n");

        // 1. 查找空闲内存块
        int freeFrame = findFreeFrame();
        if (freeFrame != -1) {
            loadPage(page, freeFrame);
            return "缺页";
        }

        // 2. 执行FIFO页面置换
        int victimFrame = findVictimFrame();
        MemoryBlock victimBlock = physicalMemory[victimFrame];
        int victimPage = victimBlock.page;

        // 3. 处理被修改的页面
        if (victimBlock.modified) {
            logArea.append("  写回页" + victimPage + "到磁盘位置" + pageTable[victimPage].diskLocation + "\n");

        }

        // 4. 加载新页面
        replacePage(victimFrame, page);
        return "淘汰第" + victimPage + "页";
    }

    /**
     * 查找空闲内存块
     * @return 空闲块号，找不到返回-1
     */
    private int findFreeFrame() {
        for (int i = 0; i < allocatedBlocks; i++) {
            if (physicalMemory[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * FIFO算法查找淘汰页
     * @return 淘汰页的块号
     */
    private int findVictimFrame() {
        int oldest = 0;
        long minTime = Long.MAX_VALUE;
        // 遍历查找最早加载的页面
        for (int i = 0; i < allocatedBlocks; i++) {
            if (physicalMemory[i] != null &&
                    physicalMemory[i].loadTime < minTime) {
                minTime = physicalMemory[i].loadTime;
                oldest = i;
            }
        }
        return oldest;
    }

    /**
     * 加载页面到指定内存块
     * @param page 要加载的页号
     * @param frame 目标内存块号
     */
    private void loadPage(int page, int frame) {
        MemoryBlock old = physicalMemory[frame];
        if (old != null) {
            physicalMemory[frame] = null;
        }

        physicalMemory[frame] = new MemoryBlock(page, System.currentTimeMillis());
        pageTable[page].present = true;
        pageTable[page].frame = frame;
        logArea.append("  加载页" + page + "到内存块" + frame + "\n");
    }

    /**
     * 执行页面置换
     * @param victimFrame 被置换的块号
     * @param newPage 要加载的新页号
     */
    private void replacePage(int victimFrame, int newPage) {
        int oldPage = physicalMemory[victimFrame].page;
        pageTable[oldPage].present = false;  // 标记旧页不在内存
        loadPage(newPage, victimFrame);      // 加载新页
    }

    /**
     * 地址有效性验证
     * @param page 页号
     * @param offset 页内偏移
     * @return 是否有效
     */
    private boolean invalidAddress(int page, int offset) {
        return (page < 0) || (page >= 64) || (offset < 0) || (offset >= 1024);
    }

    //界面刷新方法

    /**
     * 刷新页表显示
     */
    private void refreshPageTable() {
        pageTableModel.setRowCount(0);  // 清空现有数据
        for (int i = 0; i < 64; i++) {
            PageTableEntry e = pageTable[i];
            pageTableModel.addRow(new Object[]{
                    i,
                    e.present, e.present ? e.frame : "N/A",
                    e.modified ? "✔" : "✖", String.format("%03d", e.diskLocation)
            });
        }
    }

    /**
     * 刷新内存状态表
     */
    private void refreshMemoryTable() {
        memoryModel.setRowCount(0);
        for (int i = 0; i < allocatedBlocks; i++) {
            MemoryBlock b = physicalMemory[i];
            memoryModel.addRow(new Object[]{
                    i,
                    (b != null) ? b.page : "空",
                    (b != null) ? TIME_FORMAT.format(new Date(b.loadTime)) : "N/A"
            });
        }
    }

    /**
     * 添加历史记录
     * @param operation 操作类型
     * @param page 页号
     * @param addr 物理地址
     * @param result 操作结果
     */
    private void addHistoryRecord(String operation, int page, int addr, String result) {
        historyModel.addRow(new Object[]{
                historyCounter++,
                TIME_FORMAT.format(new Date()),
                operation,
                page,
                (addr != -1) ? addr : "N/A",
                result
        });
    }

    /**
     * 全局界面刷新
     */
    private void refreshAll() {
        refreshPageTable();    // 刷新页表
        refreshMemoryTable();  // 刷新内存状态
        repaint();             // 重绘界面
    }

    /**
     * 设置全局字体
     * @param font 目标字体
     */

    private void setUIFont(Font font) {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof Font) {
                UIManager.put(key, font);
            }
        }
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
//import javax.swing.table.DefaultTableCellRenderer;
//import javax.swing.table.DefaultTableModel;
//import javax.swing.table.TableCellRenderer;
//import java.awt.*;
//import java.text.SimpleDateFormat;
//import java.util.*;
//
//public class PagingSimulator extends JFrame {
//    //============= 系统配置 =============//
//    private static final int TOTAL_MEMORY = 64 * 1024;
//    private int allocatedBlocks = 4;
//    private SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
//    private int historyCounter = 1;
//    private static final Font DEFAULT_FONT = new Font("微软雅黑", Font.PLAIN, 16);
//
//    //============= 核心数据结构 =============//
//    static class PageTableEntry {
//        boolean present; //存在标志
//        int frame; //内存块号
//        boolean modified; // 永久修改标志
//        int diskLocation; //磁盘位置
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
//        boolean modified; // 内存中的修改状态
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
//        setUIFont(DEFAULT_FONT);
//    }
//
//    private void initSystem() {
//        int currentDiskLoc = 100;
//        Random rand = new Random();
//        for (int i = 0; i < 64; i++) {
//            pageTable[i] = new PageTableEntry(false, -1, false, currentDiskLoc);
//            currentDiskLoc += 1 + rand.nextInt(20);
//        }
//        physicalMemory = new MemoryBlock[allocatedBlocks];
//        Arrays.fill(physicalMemory, null);
//    }
//
//    private void initComponents() {
//        setTitle("动态分页存储管理模拟器");
//        setSize(1600, 850);
//        setDefaultCloseOperation(EXIT_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        // 控制面板
//        JPanel controlPanel = new JPanel();
//        blockCountField = new JTextField("4", 5);
//        JButton initBtn = new JButton("应用配置");
//        opCombo = new JComboBox<>(new String[]{"save", "load", "+", "-", "*", "/"});
//        pageField = new JTextField(6);
//        offsetField = new JTextField(6);
//        JButton execButton = new JButton("执行指令");
//
//        // 设置组件字体
//        Font controlFont = new Font("微软雅黑", Font.PLAIN, 16);
//        blockCountField.setFont(controlFont);
//        initBtn.setFont(controlFont);
//        opCombo.setFont(controlFont);
//        pageField.setFont(controlFont);
//        offsetField.setFont(controlFont);
//        execButton.setFont(controlFont);
//
//        controlPanel.add(new JLabel(" 内存块数(4-64): "));
//        controlPanel.add(blockCountField);
//        controlPanel.add(initBtn);
//        controlPanel.add(new JLabel(" 操作类型: "));
//        controlPanel.add(opCombo);
//        controlPanel.add(new JLabel(" 页号(0-63): "));
//        controlPanel.add(pageField);
//        controlPanel.add(new JLabel(" 页内地址: "));
//        controlPanel.add(offsetField);
//        controlPanel.add(execButton);
//        add(controlPanel, BorderLayout.NORTH);
//
//        // 主显示面板
//        JPanel mainPanel = new JPanel(new GridLayout(1, 3, 10, 0));
//
//        // 页表
//        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "内存块号", "修改", "磁盘位置"}, 0);
//        JTable pageTable = new JTable(pageTableModel) {
//            @Override
//            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
//                Component c = super.prepareRenderer(renderer, row, column);
//                boolean isPresent = (Boolean) getModel().getValueAt(row, 1);
//                c.setBackground(isPresent ? new Color(200, 255, 200) : getBackground());
//                return c;
//            }
//        };
//        pageTable.setFont(DEFAULT_FONT);
//        pageTable.getTableHeader().setFont(DEFAULT_FONT);
//        pageTable.setRowHeight(30);
//        JScrollPane leftScroll = new JScrollPane(pageTable);
//        leftScroll.setBorder(BorderFactory.createTitledBorder("页表"));
//
//        // 内存表
//        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "加载时间"}, 0);
//        JTable memoryTable = new JTable(memoryModel);
//        memoryTable.setFont(DEFAULT_FONT);
//        memoryTable.getTableHeader().setFont(DEFAULT_FONT);
//        memoryTable.setRowHeight(30);
//        JScrollPane centerScroll = new JScrollPane(memoryTable);
//        centerScroll.setBorder(BorderFactory.createTitledBorder("内存状态"));
//
//        // 历史记录
//        historyModel = new DefaultTableModel(new Object[]{"序号", "时间", "操作", "页号", "物理地址", "结果"}, 0);
//        JTable historyTable = new JTable(historyModel);
//        historyTable.setFont(DEFAULT_FONT);
//        historyTable.getTableHeader().setFont(DEFAULT_FONT);
//        historyTable.setRowHeight(30);
//        JScrollPane rightScroll = new JScrollPane(historyTable);
//        rightScroll.setBorder(BorderFactory.createTitledBorder("操作历史"));
//
//        mainPanel.add(leftScroll);
//        mainPanel.add(centerScroll);
//        mainPanel.add(rightScroll);
//        add(mainPanel, BorderLayout.CENTER);
//
//        // 日志区域
//        logArea = new JTextArea();
//        logArea.setFont(DEFAULT_FONT);
//        add(new JScrollPane(logArea), BorderLayout.SOUTH);
//
//        // 事件监听
//        initBtn.addActionListener(e -> applyConfig());
//        execButton.addActionListener(e -> executeInstruction());
//        refreshAll();
//    }
//
//    private void applyConfig() {
//        try {
//            int newCount = Integer.parseInt(blockCountField.getText());
//            if (newCount < 4 || newCount > 64) {
//                JOptionPane.showMessageDialog(this, "内存块数需在4-64之间!");
//                return;
//            }
//            allocatedBlocks = newCount;
//            initSystem();
//            historyModel.setRowCount(0);
//            historyCounter = 1;
//            refreshAll();
//            logArea.append("系统已重置：内存块数=" + allocatedBlocks + "\n");
//        } catch (NumberFormatException ex) {
//            JOptionPane.showMessageDialog(this, "请输入有效数字!");
//        }
//    }
//
//    private void executeInstruction() {
//        try {
//            String operation = (String) opCombo.getSelectedItem();
//            int page = Integer.parseInt(pageField.getText());
//            int offset = Integer.parseInt(offsetField.getText());
//            int physicalAddr = -1;
//            String result = "";
//
//            if (invalidAddress(page, offset)) {
//                logArea.append("错误: 无效地址!\n");
//                addHistoryRecord(operation, page, physicalAddr, "无效地址");
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
//            if ("save".equals(operation) && entry.present) {
//                entry.modified = true;
//                physicalMemory[entry.frame].modified = true;
//            }
//
//            addHistoryRecord(operation, page, physicalAddr, result);
//            refreshAll();
//
//        } catch (NumberFormatException ex) {
//            logArea.append("错误: 输入格式错误!\n");
//        }
//    }
//
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
//        MemoryBlock victimBlock = physicalMemory[victimFrame];
//        int victimPage = victimBlock.page;
//
//        if (victimBlock.modified) {
//            logArea.append("  写回页" + victimPage + "到磁盘位置"
//                    + pageTable[victimPage].diskLocation + "\n");
//            pageTable[victimPage].modified = true; // 保持修改标志为true
//        }
//
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
//        MemoryBlock old = physicalMemory[frame];
//        if (old != null) {
//            physicalMemory[frame] = null;
//        }
//
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
//    private boolean invalidAddress(int page, int offset) {
//        return (page < 0) || (page >= 64) || (offset < 0) || (offset >= 1024);
//    }
//
//    private void refreshPageTable() {
//        pageTableModel.setRowCount(0);
//        for (int i = 0; i < 64; i++) {
//            PageTableEntry e = pageTable[i];
//            pageTableModel.addRow(new Object[]{
//                    i,
//                    e.present,
//                    e.present ? e.frame : "N/A",
//                    e.modified ? "✔" : "✖",
//                    String.format("%03d", e.diskLocation)
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
//                    (b != null) ? TIME_FORMAT.format(new Date(b.loadTime)) : "N/A"
//            });
//        }
//    }
//
//    private void addHistoryRecord(String operation, int page, int addr, String result) {
//        historyModel.addRow(new Object[]{
//                historyCounter++,
//                TIME_FORMAT.format(new Date()),
//                operation,
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
//    private void setUIFont(Font font) {
//        Enumeration<Object> keys = UIManager.getDefaults().keys();
//        while (keys.hasMoreElements()) {
//            Object key = keys.nextElement();
//            if (UIManager.get(key) instanceof Font) {
//                UIManager.put(key, font);
//            }
//        }
//    }
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> {
//            PagingSimulator frame = new PagingSimulator();
//            frame.setVisible(true);
//        });
//    }
//}
