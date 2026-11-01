package trainlab;

import java.io.*;
import java.util.*;

public class DatabaseManager {
    private static final String DB_FILE = "trains.db";
    private static final String IDX_FILE = "trains.idx";
    
    // Размер одной записи в байтах: 
    // 4 (int) + 20 (10 chars * 2) + 60 (30 chars * 2) + 10 (5 chars * 2) + 8 (double) = 102 байта
    public static final int RECORD_SIZE = 102; 

    // Индекс: ID -> смещение в файле (offset)
    private Map<Integer, Long> index = new HashMap<>();
    private RandomAccessFile raf;

    public DatabaseManager() throws IOException {
        File db = new File(DB_FILE);
        raf = new RandomAccessFile(db, "rw");
        loadIndex();
    }

    // --- Загрузка и сохранение индекса ---
    private void loadIndex() {
        File idxFile = new File(IDX_FILE);
        if (idxFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(idxFile))) {
                index = (Map<Integer, Long>) ois.readObject();
            } catch (Exception e) {
                System.out.println("Ошибка загрузки индекса: " + e.getMessage());
            }
        }
    }

    public void saveIndex() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(IDX_FILE))) {
            oos.writeObject(index);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Вспомогательные методы для фиксированных строк ---
    private void writeFixedString(String s, int length, RandomAccessFile raf) throws IOException {
        StringBuilder sb = new StringBuilder(s);
        sb.setLength(length); // Обрезает или дополняет нулевыми символами
        raf.writeChars(sb.toString());
    }

    private String readFixedString(int length, RandomAccessFile raf) throws IOException {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = raf.readChar();
        }
        return new String(chars).replace("\0", "").trim();
    }

    // --- CRUD Операции ---

    public boolean addTrain(Train t) throws IOException {
        if (index.containsKey(t.getId())) return false; // Проверка уникальности

        long offset = raf.length();
        raf.seek(offset);
        
        raf.writeInt(t.getId());
        writeFixedString(t.getTrainNumber(), 10, raf);
        writeFixedString(t.getDestination(), 30, raf);
        writeFixedString(t.getDepartureTime(), 5, raf);
        raf.writeDouble(t.getPrice());

        index.put(t.getId(), offset);
        saveIndex();
        return true;
    }

    public Train getTrainById(int id) throws IOException {
        if (!index.containsKey(id)) return null;
        long offset = index.get(id);
        raf.seek(offset);
        
        int readId = raf.readInt();
        if (readId == -1) return null; // Удалено
        
        return new Train(readId, readFixedString(10, raf), readFixedString(30, raf), readFixedString(5, raf), raf.readDouble());
    }

    public boolean deleteById(int id) throws IOException {
        if (!index.containsKey(id)) return false;
        long offset = index.get(id);
        raf.seek(offset);
        raf.writeInt(-1); // Маркер удаления
        index.remove(id);
        saveIndex();
        return true;
    }

    public List<Train> searchByDestination(String dest) throws IOException {
        List<Train> result = new ArrayList<>();
        for (long offset : index.values()) {
            raf.seek(offset);
            int id = raf.readInt();
            if (id != -1) {
                String num = readFixedString(10, raf);
                String d = readFixedString(30, raf);
                String time = readFixedString(5, raf);
                double price = raf.readDouble();
                if (d.toLowerCase().contains(dest.toLowerCase())) {
                    result.add(new Train(id, num, d, time, price));
                }
            }
        }
        return result;
    }

    public boolean editTrain(Train t) throws IOException {
        if (!index.containsKey(t.getId())) return false;
        long offset = index.get(t.getId());
        raf.seek(offset);
        
        raf.writeInt(t.getId());
        writeFixedString(t.getTrainNumber(), 10, raf);
        writeFixedString(t.getDestination(), 30, raf);
        writeFixedString(t.getDepartureTime(), 5, raf);
        raf.writeDouble(t.getPrice());
        return true;
    }

    public List<Train> getAll() throws IOException {
        List<Train> list = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : index.entrySet()) {
            Train t = getTrainById(entry.getKey());
            if (t != null) list.add(t);
        }
        return list;
    }

    public void clearDatabase() throws IOException {
        raf.setLength(0);
        index.clear();
        saveIndex();
    }

    // --- Бэкап и Импорт ---
    public void backup(String path) throws IOException {
        java.nio.file.Files.copy(new File(DB_FILE).toPath(), new File(path).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        java.nio.file.Files.copy(new File(IDX_FILE).toPath(), new File(path + ".idx").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public void restore(String path) throws IOException {
        raf.close();
        java.nio.file.Files.copy(new File(path).toPath(), new File(DB_FILE).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        java.nio.file.Files.copy(new File(path + ".idx").toPath(), new File(IDX_FILE).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        raf = new RandomAccessFile(DB_FILE, "rw");
        loadIndex();
    }

    // Импорт из CSV (id;number;dest;time;price)
    public void importFromCSV(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length == 5) {
                    Train t = new Train(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        parts[2],
                        parts[3],
                        Double.parseDouble(parts[4])
                    );
                    addTrain(t);
                }
            }
        }
    }
}