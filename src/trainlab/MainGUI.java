package trainlab;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class MainGUI extends JFrame {
    private DatabaseManager db;
    private DefaultTableModel tableModel;
    private JTable table;

    public MainGUI() {
        try {
            db = new DatabaseManager();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка инициализации БД");
            System.exit(1);
        }

        setTitle("БД Пригородные электрички");
        setSize(800, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

     
        String[] columns = {"ID", "Номер поезда", "Направление", "Время", "Цена"};
        tableModel = new DefaultTableModel(columns, 0);
        table = new JTable(tableModel);

        tableModel.addTableModelListener(e -> {
           
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                try {
 
                    int id = Integer.parseInt(tableModel.getValueAt(row, 0).toString());
                    String num = tableModel.getValueAt(row, 1).toString();
                    String dest = tableModel.getValueAt(row, 2).toString();
                    String time = tableModel.getValueAt(row, 3).toString();
                   
                    double price = Double.parseDouble(tableModel.getValueAt(row, 4).toString().replace(",", "."));

                    db.editTrain(new Train(id, num, dest, time, price));
                    
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Ошибка! Цена должна быть числом (например, 250.50)", "Ошибка ввода", JOptionPane.ERROR_MESSAGE);
                    
                    refreshTable(); 
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Панель кнопок
        JPanel panel = new JPanel(new GridLayout(2, 5, 5, 5));
        
        JButton btnRefresh = new JButton("Обновить");
        JButton btnAdd = new JButton("Добавить");
        JButton btnEdit = new JButton("Редактировать");
        JButton btnDelete = new JButton("Удалить");
        JButton btnClear = new JButton("Очистить БД");
        JButton btnSearch = new JButton("Поиск по городу");
        JButton btnBackup = new JButton("Бэкап");
        JButton btnRestore = new JButton("Восстановить");
        JButton btnImport = new JButton("Импорт CSV");

        panel.add(btnRefresh); panel.add(btnAdd); panel.add(btnEdit); panel.add(btnDelete); panel.add(btnClear);
        panel.add(btnSearch); panel.add(btnBackup); panel.add(btnRestore); panel.add(btnImport);
        add(panel, BorderLayout.SOUTH);

        
        btnRefresh.addActionListener(e -> refreshTable());
        
        btnAdd.addActionListener(e -> {
            try {
                int id = Integer.parseInt(JOptionPane.showInputDialog("Введите ID (число):"));
                String num = JOptionPane.showInputDialog("Номер поезда:");
                String dest = JOptionPane.showInputDialog("Станция отправления - станция прибытия:");
                String time = JOptionPane.showInputDialog("Время (ЧЧ:ММ):");
                double price = Double.parseDouble(JOptionPane.showInputDialog("Цена:"));
                
                if (db.addTrain(new Train(id, num, dest, time, price))) {
                    refreshTable();
                } else {
                    JOptionPane.showMessageDialog(this, "Ошибка: ID уже существует!");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Некорректный ввод!");
            }
        });

        btnDelete.addActionListener(e -> {
            try {
                int id = Integer.parseInt(JOptionPane.showInputDialog("Введите ID для удаления:"));
                if (db.deleteById(id)) refreshTable();
                else JOptionPane.showMessageDialog(this, "ID не найден!");
            } catch (Exception ex) { }
        });

        btnEdit.addActionListener(e -> {
            try {
                int id = Integer.parseInt(JOptionPane.showInputDialog("Введите ID для редактирования:"));
                Train t = db.getTrainById(id);
                if (t == null) {
                    JOptionPane.showMessageDialog(this, "ID не найден!");
                    return;
                }
                String num = JOptionPane.showInputDialog("Новый номер поезда:", t.getTrainNumber());
                String dest = JOptionPane.showInputDialog("Новое направление:", t.getDestination());
                String time = JOptionPane.showInputDialog("Новое время:", t.getDepartureTime());
                double price = Double.parseDouble(JOptionPane.showInputDialog("Новая цена:", t.getPrice()));
                
                db.editTrain(new Train(id, num, dest, time, price));
                refreshTable();
            } catch (Exception ex) { }
        });

        btnClear.addActionListener(e -> {
            try {
                db.clearDatabase();
                refreshTable();
            } catch (IOException ex) { }
        });

        btnSearch.addActionListener(e -> {
            try {
                String dest = JOptionPane.showInputDialog("Введите направление для поиска (или часть):");
                if (dest == null || dest.isEmpty()) return;
                List<Train> result = db.searchByDestination(dest);
                tableModel.setRowCount(0);
                for (Train t : result) {
                    tableModel.addRow(new Object[]{t.getId(), t.getTrainNumber(), t.getDestination(), t.getDepartureTime(), t.getPrice()});
                }
            } catch (IOException ex) { }
        });

        btnBackup.addActionListener(e -> {
            try {
                db.backup("backup_trains.db");
                JOptionPane.showMessageDialog(this, "Бэкап создан: backup_trains.db");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Ошибка бэкапа");
            }
        });

        btnRestore.addActionListener(e -> {
            try {
                db.restore("backup_trains.db");
                refreshTable();
                JOptionPane.showMessageDialog(this, "БД восстановлена из бэкапа");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Ошибка восстановления (нет файла бэкапа?)");
            }
        });

        btnImport.addActionListener(e -> {
            try {
                JFileChooser chooser = new JFileChooser();
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    db.importFromCSV(chooser.getSelectedFile().getAbsolutePath());
                    refreshTable();
                    JOptionPane.showMessageDialog(this, "Импорт завершен!");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Ошибка импорта (проверьте формат CSV: id;num;dest;time;price)");
            }
        });

        refreshTable();
        setVisible(true);
    }

    private void refreshTable() {
        try {
            tableModel.setRowCount(0);
            for (Train t : db.getAll()) {
                tableModel.addRow(new Object[]{t.getId(), t.getTrainNumber(), t.getDestination(), t.getDepartureTime(), t.getPrice()});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainGUI::new);
    }
}