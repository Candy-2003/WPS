package os.dynamicpartitioning.view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * 动态分区存储管理模拟器GUI界面
 */
public class PartitionView extends JFrame {

    // ====================== 核心数据结构 ======================
    /**
     * 内存块类（静态内部类）
     * start - 起始地址（单位KB）
     * size - 块大小（单位KB）
     * free - 是否空闲（true表示空闲）
     */
    static class MemoryBlock {
        int start;
        int size;
        boolean free;
        public MemoryBlock(int start, int size, boolean free) {
            this.start = start;
            this.size = size;
            this.free = free;
        }
    }
    //DefaultTableModel的实现，其使用Vector的Vectors来存储单元格值对象。

    // ====================== GUI组件 ======================
    private DefaultTableModel tableModel;      // 表格数据模型,构造一个默认的 DefaultTableModel 是一个零列和零行的表
    private JTable partitionTable;            // 显示分区信息的表格
    private JComboBox<String> algoCombo;      // 算法选择下拉框
    private JTextField sizeField;             // 输入分配大小的文本框
    private List<MemoryBlock> memoryBlocks = new ArrayList<>(); // 内存块列表

    // ====================== 初始化方法 ======================
    /**
     * 构造方法
     */
    public PartitionView() {
        initComponents();  // 初始化GUI组件
        initMemory();      // 初始化内存（默认1024KB）
    }

    /**
     * 初始化GUI组件布局
     */
    public void initComponents() {
        setTitle("动态分区存储管理模拟器");
        setSize(800, 600);
//        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ---------- 控制面板（北区） ----------
        JPanel controlPanel = new JPanel();
        sizeField = new JTextField(10);       // 分配大小输入框
        JButton allocButton = new JButton("分配内存");
        JButton freeButton = new JButton("释放内存");
        algoCombo = new JComboBox<>(new String[]{"最先适应", "最佳适应", "最坏适应"}); // 算法选择器

        controlPanel.add(new JLabel("选择算法:"));
        controlPanel.add(algoCombo);
        controlPanel.add(new JLabel("大小(KB):"));
        controlPanel.add(sizeField);
        controlPanel.add(allocButton);
        controlPanel.add(freeButton);
        add(controlPanel, BorderLayout.NORTH);

        // ---------- 分区表格（中区） ----------
        tableModel = new DefaultTableModel(new Object[]{"起始地址", "大小", "状态"}, 0);
        partitionTable = new JTable(tableModel);
        add(new JScrollPane(partitionTable), BorderLayout.CENTER);

        // ---------- 内存可视化面板（南区） ----------
        JPanel visualPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int y = 10;  // 起始绘制纵坐标
//                int y = 10;  // 起始绘制纵坐标
                Font labelFont = new Font("Times New Roman", Font.BOLD, 12); // 创建新字体
                g.setFont(labelFont);
                // 遍历内存块并绘制矩形
                for (MemoryBlock block : memoryBlocks) {
                    int height = block.size * 1024 / 1024;  // 根据比例计算高度
                    g.setColor(block.free ? Color.GREEN : Color.RED); // 空闲绿色，已分配红色
                    g.fillRect(50, y, 200, height);       // 绘制矩形块
                    g.setColor(Color.BLACK);
                    g.drawString(block.start + "K-" + (block.start + block.size) + "K", 260, y+5); // 标注地址范围
//                    g.drawString(block.start + "K-" + (block.start + block.size) + "K", 260, y + 15); // 标注地址范围
//                    y += height + 5;  // 更新纵坐标
                    y += height + 10;  // 更新纵坐标
                }
            }
            // ===== 3. 动态计算面板大小 =====
            @Override
            public Dimension getPreferredSize() {
                int totalHeight = 10; // 初始偏移
                for (MemoryBlock block : memoryBlocks) {
                    int blockHeight = block.size * 1024 / 1024;
                    totalHeight += blockHeight + 10; // 块高 + 间隔
                }
                return new Dimension(400, Math.max(totalHeight, 500)); // 最小高度500
            }
        };
        // ===== 4. 添加滚动条容器 =====
        JScrollPane visualScrollPane = new JScrollPane(visualPanel);
        visualScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        visualScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(visualScrollPane, BorderLayout.EAST);
        // 新增：强制更新滚动面板布局
        visualScrollPane.revalidate();
        visualScrollPane.repaint();
//        visualPanel.setPreferredSize(new Dimension(400, 500));
//        add(visualPanel, BorderLayout.EAST);

        // ---------- 事件监听 ----------
        allocButton.addActionListener(e -> allocateMemory()); // 分配按钮点击事件
        freeButton.addActionListener(e -> freeMemory());      // 释放按钮点击事件
    }

    // ====================== 内存管理核心逻辑 ======================
    /**
     * 初始化内存（默认1024KB空闲块）
     */
    private void initMemory() {
        memoryBlocks.clear();
        memoryBlocks.add(new MemoryBlock(0, 1024, true));
        refreshTable();
    }

    /**
     * 分配内存主逻辑
     */
    private void allocateMemory() {
        try {
            int size = Integer.parseInt(sizeField.getText());  // 获取输入大小
            String algo = (String) algoCombo.getSelectedItem(); // 获取选择的算法

            boolean success = false;
            switch (algo) {  // 根据算法调用不同分配策略
                case "最先适应":
                    success = firstFit(size);
                    break;
                case "最佳适应":
                    success = bestFit(size);
                    break;
                case "最坏适应":
                    success = worstFit(size);
                    break;
            }

            if (!success) {
                JOptionPane.showMessageDialog(this, "分配失败: 没有足够的连续空间!");
            }
            refreshTable();  // 刷新界面
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字!");
        }
    }

    /**
     * 最先适应算法
     * @param size 需要分配的大小
     * @return 是否分配成功
     */
    private boolean firstFit(int size) {
        for (int i = 0; i < memoryBlocks.size(); i++) {
            MemoryBlock block = memoryBlocks.get(i);
            if (block.free && block.size >= size) {  // 找到第一个满足条件的块
                splitBlock(i, size);  // 分割内存块
                return true;
            }
        }
        return false;
    }

    /**
     * 最佳适应算法（找能满足条件的最小块）
     */
    private boolean bestFit(int size) {
        int bestIndex = -1;
        int minSize = Integer.MAX_VALUE;
        for (int i = 0; i < memoryBlocks.size(); i++) {
            MemoryBlock block = memoryBlocks.get(i);
            if (block.free && block.size >= size && block.size < minSize) {
                bestIndex = i;
                minSize = block.size;
            }
        }
        if (bestIndex != -1) {
            splitBlock(bestIndex, size);
            return true;
        }
        return false;
    }

    /**
     * 最坏适应算法（找能满足条件的最大块）
     */
    private boolean worstFit(int size) {
        int worstIndex = -1;
        int maxSize = -1;
        for (int i = 0; i < memoryBlocks.size(); i++) {
            MemoryBlock block = memoryBlocks.get(i);
            if (block.free && block.size >= size && block.size > maxSize) {
                worstIndex = i;
                maxSize = block.size;
            }
        }
        if (worstIndex != -1) {
            splitBlock(worstIndex, size);
            return true;
        }
        return false;
    }

    /**
     * 分割内存块（核心方法）
     * @param index 要分割的块索引
     * @param size 需要分配的大小
     */
    private void splitBlock(int index, int size) {
        MemoryBlock original = memoryBlocks.get(index);
        if (original.size == size) {  // 块大小刚好等于需求
            original.free = false;    // 直接标记为已分配
        } else {  // 需要分割成两个块
            MemoryBlock allocated = new MemoryBlock(original.start, size, false); // 已分配块
            MemoryBlock remaining = new MemoryBlock(  // 剩余空闲块
                    original.start + size,
                    original.size - size,
                    true
            );
            memoryBlocks.remove(index);        // 移除原块
            memoryBlocks.add(index, remaining); // 先添加剩余块（保证顺序）
            memoryBlocks.add(index, allocated); // 再添加已分配块
        }
    }

    /**
     * 释放内存
     */
    private void freeMemory() {
        int selectedRow = partitionTable.getSelectedRow();
        if (selectedRow == -1) {  // 未选择行
            JOptionPane.showMessageDialog(this, "请先选择要释放的分区!");
            return;
        }

        MemoryBlock block = memoryBlocks.get(selectedRow);
        if (block.free) {  // 已经是空闲块
            JOptionPane.showMessageDialog(this, "该分区已经是空闲状态!");
            return;
        }

        block.free = true;  // 标记为空闲
        mergeBlocks();      // 合并相邻空闲块
        refreshTable();     // 刷新界面
    }

    /**
     * 合并相邻空闲块（防止碎片）
     */
    private void mergeBlocks() {
        for (int i = 0; i < memoryBlocks.size() - 1; i++) {
            MemoryBlock current = memoryBlocks.get(i);
            MemoryBlock next = memoryBlocks.get(i + 1);
            // 相邻且都空闲时合并
            if (current.free && next.free &&
                    current.start + current.size == next.start) {
                current.size += next.size;     // 合并大小
                memoryBlocks.remove(i + 1);   // 移除后一个块
                i--;  // 回退索引，继续检查合并后的块
            }
        }
    }

    /**
     * 刷新表格数据（同步内存状态到界面）
     */
    private void refreshTable() {
        tableModel.setRowCount(0);  // 清空表格
        for (MemoryBlock block : memoryBlocks) {
            tableModel.addRow(new Object[]{
                    block.start + "K",
                    block.size + "K",
                    block.free ? "空闲" : "已分配"  // 状态显示
            });
        }

        repaint();  // 触发可视化面板重绘
    }

    // ====================== 主方法 ======================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {  // 确保GUI线程安全
            PartitionView frame = new PartitionView();
            frame.setVisible(true);
        });
    }
}
//package os.dynamicpartitioning.view;
//
//import javax.swing.*;
//import javax.swing.table.DefaultTableModel;
//import java.awt.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.util.ArrayList;
//import java.util.List;
//
//public class PartitionView extends JFrame {
//
//     //内存分配逻辑
//    private void allocateMemory() {
//        try {
//            int size = Integer.parseInt(sizeField.getText());
//            String algo = (String) algoCombo.getSelectedItem();
//
//            boolean success = false;
//            switch (algo) {
//                case "最先适应":
//                    success = firstFit(size);
//                    break;
//                case "最佳适应":
//                    success = bestFit(size);
//                    break;
//                case "最坏适应":
//                    success = worstFit(size);
//                    break;
//            }
//
//            if (!success) {
//                JOptionPane.showMessageDialog(this, "分配失败: 没有足够的连续空间!");
//            }
//            refreshTable();
//        } catch (NumberFormatException ex) {
//            JOptionPane.showMessageDialog(this, "请输入有效的数字!");
//        }
//    }
//
//    // 最先适应算法
//    private boolean firstFit(int size) {
//        for (int i = 0; i < memoryBlocks.size(); i++) {
//            MemoryBlock block = memoryBlocks.get(i);
//            if (block.free && block.size >= size) {
//                splitBlock(i, size);
//                return true;
//            }
//        }
//        return false;
//    }
//
//    // 最佳适应算法
//    private boolean bestFit(int size) {
//        int bestIndex = -1;
//        int minSize = Integer.MAX_VALUE;
//        for (int i = 0; i < memoryBlocks.size(); i++) {
//            MemoryBlock block = memoryBlocks.get(i);
//            if (block.free && block.size >= size && block.size < minSize) {
//                bestIndex = i;
//                minSize = block.size;
//            }
//        }
//        if (bestIndex != -1) {
//            splitBlock(bestIndex, size);
//            return true;
//        }
//        return false;
//    }
//
//    // 最坏适应算法
//    private boolean worstFit(int size) {
//        int worstIndex = -1;
//        int maxSize = -1;
//        for (int i = 0; i < memoryBlocks.size(); i++) {
//            MemoryBlock block = memoryBlocks.get(i);
//            if (block.free && block.size >= size && block.size > maxSize) {
//                worstIndex = i;
//                maxSize = block.size;
//            }
//        }
//        if (worstIndex != -1) {
//            splitBlock(worstIndex, size);
//            return true;
//        }
//        return false;
//    }
//
//    // 分割内存块
//    private void splitBlock(int index, int size) {
//        MemoryBlock original = memoryBlocks.get(index);
//        if (original.size == size) {
//            original.free = false;
//        } else {
//            MemoryBlock allocated = new MemoryBlock(original.start, size, false);
//            MemoryBlock remaining = new MemoryBlock(
//                    original.start + size,
//                    original.size - size,
//                    true
//            );
//            memoryBlocks.remove(index);
//            memoryBlocks.add(index, remaining);
//            memoryBlocks.add(index, allocated);
//        }
//    }
//
//    // 内存释放逻辑
//    private void freeMemory() {
//        int selectedRow = partitionTable.getSelectedRow();
//        if (selectedRow == -1) {
//            JOptionPane.showMessageDialog(this, "请先选择要释放的分区!");
//            return;
//        }
//
//        MemoryBlock block = memoryBlocks.get(selectedRow);
//        if (block.free) {
//            JOptionPane.showMessageDialog(this, "该分区已经是空闲状态!");
//            return;
//        }
//
//        block.free = true;
//        mergeBlocks();
//        refreshTable();
//    }
//
//    // 合并相邻空闲块
//    private void mergeBlocks() {
//        for (int i = 0; i < memoryBlocks.size() - 1; i++) {
//            MemoryBlock current = memoryBlocks.get(i);
//            MemoryBlock next = memoryBlocks.get(i + 1);
//            if (current.free && next.free &&
//                    current.start + current.size == next.start) {
//                current.size += next.size;
//                memoryBlocks.remove(i + 1);
//                i--; // 继续检查合并后的块
//            }
//        }
//    }
//    // 核心数据结构：内存块
//    static class MemoryBlock {
//        int start;
//        int size;
//        boolean free;
//
//        public MemoryBlock(int start, int size, boolean free) {
//            this.start = start;
//            this.size = size;
//            this.free = free;
//        }
//    }
//
//    // GUI组件定义
//    private DefaultTableModel tableModel;
//    private JTable partitionTable;
//    private JComboBox<String> algoCombo;
//    private JTextField sizeField;
//    private List<MemoryBlock> memoryBlocks = new ArrayList<>();
//
//    // 构造方法（修复：去掉 void）
//    public PartitionView() {
//        initComponents();
//        initMemory();
//    }
//
//    private void initComponents() {
//        setTitle("动态分区存储管理模拟器");
//        setSize(800, 600);
//        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        setLayout(new BorderLayout());
//
//        // 控制面板（北区）
//        JPanel controlPanel = new JPanel();
//        sizeField = new JTextField(10);
//        JButton allocButton = new JButton("分配内存");
//        JButton freeButton = new JButton("释放内存");
//        algoCombo = new JComboBox<>(new String[]{"最先适应", "最佳适应", "最坏适应"});
//
//        controlPanel.add(new JLabel("选择算法:"));
//        controlPanel.add(algoCombo);
//        controlPanel.add(new JLabel("大小(KB):"));
//        controlPanel.add(sizeField);
//        controlPanel.add(allocButton);
//        controlPanel.add(freeButton);
//        add(controlPanel, BorderLayout.NORTH);
//
//        // 分区表格（中区）
//        tableModel = new DefaultTableModel(new Object[]{"起始地址", "大小", "状态"}, 0);
//        partitionTable = new JTable(tableModel);
//        add(new JScrollPane(partitionTable), BorderLayout.CENTER);
//
//        // 内存可视化面板（南区）
//        JPanel visualPanel = new JPanel() {
//            @Override
//            protected void paintComponent(Graphics g) {
//                super.paintComponent(g);
//                int y = 10;
//                for (MemoryBlock block : memoryBlocks) {
//                    int height = block.size * 400 / 1024;
//                    g.setColor(block.free ? Color.GREEN : Color.RED);
//                    g.fillRect(50, y, 200, height);
//                    g.setColor(Color.BLACK);
//                    g.drawString(block.start + "K-" + (block.start + block.size) + "K", 260, y + 15);
//                    y += height + 5;
//                }
//            }
//        };
//        visualPanel.setPreferredSize(new Dimension(300, 500));
//        add(visualPanel, BorderLayout.EAST);
//
//        // 事件监听
//        allocButton.addActionListener(e -> allocateMemory());
//        freeButton.addActionListener(e -> freeMemory());
//    }
//
//    // 初始化内存（示例：总大小1024KB）
//    private void initMemory() {
//        memoryBlocks.clear();
//        memoryBlocks.add(new MemoryBlock(0, 1024, true));
//        refreshTable();
//    }
//
//    // 刷新表格数据
//    private void refreshTable() {
//        tableModel.setRowCount(0);
//        for (MemoryBlock block : memoryBlocks) {
//            tableModel.addRow(new Object[]{
//                    block.start + "K",
//                    block.size + "K",
//                    block.free ? "空闲" : "已分配"
//            });
//        }
//        repaint();
//    }
//
//    // 其他方法（allocateMemory、firstFit、bestFit、worstFit、splitBlock、freeMemory、mergeBlocks）保持不变
//    // 确保所有方法定义在 PartitionView 类内
//
//    public static void main(String[] args) {
//        SwingUtilities.invokeLater(() -> {
//            PartitionView frame = new PartitionView();
//            frame.setVisible(true);
//        });
//    }
//}
