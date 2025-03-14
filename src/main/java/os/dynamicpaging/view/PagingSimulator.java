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
    private static final int BLOCK_COUNT = MEMORY_SIZE / BLOCK_SIZE;  // 总内存块数64
    private static final int ALLOCATED_BLOCKS = 4;      // 分配给作业的内存块数量
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss"); // 时间格式化

    //============= 核心数据结构 =============//

    /**
     * 页表项数据结构
     * - present   : 页面是否在内存中
     * - frame     : 内存块号
     * - modified  : 是否被修改过（用于写回磁盘）
     * - diskLocation : 页面在磁盘中的位置
     */
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

    /**
     * 内存块数据结构
     * - page      : 存储的页号
     * - loadTime  : 加载到内存的时间戳（用于FIFO置换）
     * - modified  : 是否被修改过
     */
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
    private DefaultTableModel pageTableModel;    // 页表数据模型
    private DefaultTableModel memoryModel;       // 内存状态数据模型
    private DefaultTableModel historyModel;      // 操作历史数据模型
    private JTextField pageField;                // 页号输入框
    private JTextField offsetField;              // 偏移地址输入框
    private JComboBox<String> opCombo;           // 操作类型下拉框
    private JTextArea logArea;                   // 日志显示区域

    //============= 核心数据 =============//
    private PageTableEntry[] pageTable = new PageTableEntry[64];  // 页表(64个页)
    private MemoryBlock[] physicalMemory = new MemoryBlock[BLOCK_COUNT]; // 物理内存

    //============= 初始化方法 =============//

    /**
     * 构造函数：初始化界面和页表
     */
    public PagingSimulator() {
        initPageTable();
        initComponents();
    }

    /**
     * 初始化页表数据
     * - 前4个页预加载到内存
     * - 其余页初始不在内存中
     */
    private void initPageTable() {
        // 初始化前4个页在内存中
        pageTable[0] = new PageTableEntry(true, 0, false, 10);
        pageTable[1] = new PageTableEntry(true, 1, false, 12);
        pageTable[2] = new PageTableEntry(true, 2, false, 13);
        pageTable[3] = new PageTableEntry(true, 3, false, 21);

        // 初始化其他页不在内存中
        for (int i = 4; i < 64; i++) {
            pageTable[i] = new PageTableEntry(false, -1, false, 100 + i);
        }

        // 初始化内存块数据
        physicalMemory[0] = new MemoryBlock(0, System.currentTimeMillis());
        physicalMemory[1] = new MemoryBlock(1, System.currentTimeMillis());
        physicalMemory[2] = new MemoryBlock(2, System.currentTimeMillis());
        physicalMemory[3] = new MemoryBlock(3, System.currentTimeMillis());
    }

    //============= GUI初始化 =============//

    /**
     * 初始化界面组件和布局
     */
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

        // 页表显示
        pageTableModel = new DefaultTableModel(new Object[]{"页号", "存在", "内存块", "修改", "磁盘位置"}, 0);
        JTable pageTable = new JTable(pageTableModel);
        mainPanel.add(new JScrollPane(pageTable));

        // 内存状态（按加载时间排序）
        memoryModel = new DefaultTableModel(new Object[]{"块号", "页号", "加载时间", "修改"}, 0);
        JTable memoryTable = new JTable(memoryModel);
        mainPanel.add(new JScrollPane(memoryTable));

        // 操作历史（简化显示）
        historyModel = new DefaultTableModel(new Object[]{"时间", "页号", "结果"}, 0);
        JTable historyTable = new JTable(historyModel);
        mainPanel.add(new JScrollPane(historyTable));

        add(mainPanel, BorderLayout.CENTER);

        //----- 日志区域 -----
        logArea = new JTextArea();
        logArea.setEditable(false);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // 事件监听
        execButton.addActionListener(e -> executeInstruction());

        // 初始刷新界面
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
            boolean isWrite = "写入".equals(opCombo.getSelectedItem());

            // 记录操作结果
            String result = "";

            // 地址有效性检查
            if (page < 0 || page >= 64 || offset < 0 || offset >= 1024) {
                logArea.append("错误: 无效地址!\n");
                addHistoryRecord(page, "无效地址");
                return;
            }

            PageTableEntry entry = pageTable[page];
            if (!entry.present) {
                // 触发缺页处理
                result = handlePageFault(page);
            } else {
                // 正常访问
                result = "不缺页";
            }

            // 处理写操作
            if (isWrite && entry.present) {
                entry.modified = true;
                physicalMemory[entry.frame].modified = true;
            }

            // 记录操作历史
            addHistoryRecord(page, result);

            // 刷新界面
            refreshAll();

        } catch (NumberFormatException ex) {
            logArea.append("错误: 请输入有效数字!\n");
        }
    }

    /**
     * 处理缺页中断
     * @param page 请求的页号
     * @return 处理结果描述
     */
    private String handlePageFault(int page) {
        logArea.append(">>> 处理缺页: 页" + page + "\n");

        // 查找空闲内存块
        int freeFrame = findFreeFrame();
        if (freeFrame != -1) {
            loadPage(page, freeFrame);
            return "缺页";
        }

        // 执行页面置换
        int victimFrame = findVictimFrame();
        int victimPage = physicalMemory[victimFrame].page;
        replacePage(victimFrame, page);
        return "淘汰第" + victimPage + "页";
    }

    /**
     * 查找空闲内存块
     * @return 空闲块号，找不到返回-1
     */
    private int findFreeFrame() {
        for (int i = 0; i < ALLOCATED_BLOCKS; i++) {
            if (physicalMemory[i] == null) {return i;}
            if (physicalMemory[i].page == null) {return i;}
        }
        return -1;
    }

    /**
     * FIFO算法寻找置换页
     * @return 要淘汰的块号
     */
    private int findVictimFrame() {
        int oldestIndex = 0;
        long oldestTime = Long.MAX_VALUE;
        for (int i = 0; i < ALLOCATED_BLOCKS; i++) {
            if (physicalMemory[i].loadTime < oldestTime) {
                oldestTime = physicalMemory[i].loadTime;
                oldestIndex = i;
            }
        }
        return oldestIndex;
    }

    /**
     * 加载页面到内存
     * @param page 要加载的页号
     * @param frame 目标内存块号
     */
    private void loadPage(int page, int frame) {
        // 如果原页面被修改过，写回磁盘
        if (physicalMemory[frame] != null && physicalMemory[frame].modified) {
            logArea.append("  写回页" + physicalMemory[frame].page + "到磁盘位置"
                    + pageTable[physicalMemory[frame].page].diskLocation + "\n");
            pageTable[physicalMemory[frame].page].modified = false;
        }

        // 加载新页面
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
        // 标记旧页不在内存
        int oldPage = physicalMemory[victimFrame].page;
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
        for (int i = 0; i < pageTable.length; i++) {
            PageTableEntry entry = pageTable[i];
            pageTableModel.addRow(new Object[]{
                    i,
                    entry.present ? "true" : "false",
                    entry.present ? entry.frame : "",
                    entry.modified ? "true" : "false",
                    entry.diskLocation
            });
        }
    }

    /**
     * 刷新内存状态显示（按加载时间排序）
     */
    private void refreshMemoryTable() {
        memoryModel.setRowCount(0);

        // 创建可排序列表
        ArrayList<MemoryBlock> sortedBlocks = new ArrayList<>();
        for (int i = 0; i < ALLOCATED_BLOCKS; i++) {
            if (physicalMemory[i] != null) {
                sortedBlocks.add(physicalMemory[i]);
            }
        }

        // 按加载时间升序排序（最早加载的排前面）
        sortedBlocks.sort(Comparator.comparingLong(b -> b.loadTime));

        // 添加到表格
        for (MemoryBlock block : sortedBlocks) {
            memoryModel.addRow(new Object[]{
                    block.page,
                    TIME_FORMAT.format(new Date(block.loadTime)),
                    block.modified ? "true" : "false"
            });
        }
    }

    /**
     * 添加历史记录（简化版）
     * @param page 操作的页号
     * @param result 操作结果
     */
    private void addHistoryRecord(int page, String result) {
        historyModel.addRow(new Object[]{
                TIME_FORMAT.format(new Date()),
                page,
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

    //============= 主方法 =============//
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PagingSimulator frame = new PagingSimulator();
            frame.setVisible(true);
        });
    }
}