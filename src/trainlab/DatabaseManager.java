package trainlab;

import java.io.*;
import java.util.*;

public class DatabaseManager {
    private static final String DB_FILE = "trains.db";
    private static final String IDX_FILE = "trains.idx";

    private static final int LEN_NUMBER = 10;  // Номер поезда
    private static final int LEN_DEST = 60;    // Направление (вы увеличили до 60)
    private static final int LEN_TIME = 5;     // Время

    // Размер одной записи в байтах: 
    // 4 (int) + (10*2) + (60*2) + (5*2) + 8 (double) = 4 + 20 + 120 + 10 + 8 = 162 байта
    public static final int RECORD_SIZE = 4 + (LEN_NUMBER * 2) + (LEN_DEST * 2) + (LEN_TIME * 2) + 8; 

    // Индекс: ID -> смещение в файле (offset)
    private Map<Integer, Long> index = new HashMap<>();
    private RandomAccessFile raf;

    public DatabaseManager() throws IOException {
        File db = new File(DB_FILE);
        raf = new RandomAccessFile(db, "rw");
        loadIndex();
    }


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


    private void writeFixedString(String s, int length, RandomAccessFile raf) throws IOException {
        StringBuilder sb = new StringBuilder(s);
        sb.setLength(length); 
        raf.writeChars(sb.toString());
    }

    private String readFixedString(int length, RandomAccessFile raf) throws IOException {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = raf.readChar();
        }
        return new String(chars).replace("\0", "").trim();
    }


    public boolean addTrain(Train t) throws IOException {
        if (index.containsKey(t.getId())) return false; // Проверка уникальности

        long offset = raf.length();
        raf.seek(offset);
        
        raf.writeInt(t.getId());
        writeFixedString(t.getTrainNumber(), LEN_NUMBER, raf);
        writeFixedString(t.getDestination(), LEN_DEST, raf);
        writeFixedString(t.getDepartureTime(), LEN_TIME, raf);
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
        

        return new Train(
            readId, 
            readFixedString(LEN_NUMBER, raf), 
            readFixedString(LEN_DEST, raf), 
            readFixedString(LEN_TIME, raf), 
            raf.readDouble()
        );
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
                String num = readFixedString(LEN_NUMBER, raf);
                String d = readFixedString(LEN_DEST, raf);
                String time = readFixedString(LEN_TIME, raf);
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
        writeFixedString(t.getTrainNumber(), LEN_NUMBER, raf);
        writeFixedString(t.getDestination(), LEN_DEST, raf);
        writeFixedString(t.getDepartureTime(), LEN_TIME, raf);
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


public void importFromCSV(String filePath) throws IOException {
    // Явно указываем кодировку UTF-8, чтобы русские буквы читались нормально
    try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
        String line;
        boolean isFirstLine = true;

        while ((line = br.readLine()) != null) {
            // 1. Убираем невидимый BOM-символ, который добавляет Excel в начало файла
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }

            // 2. Пропускаем заголовок (первую строку)
            if (isFirstLine) {
                isFirstLine = false;
                // Если в строке есть буквы (а не только цифры), значит это заголовок — пропускаем
                if (line.toLowerCase().contains("id") || line.toLowerCase().contains("номер") || line.toLowerCase().contains("цена")) {
                    continue; 
                }
            }

            if (line.trim().isEmpty()) continue; // Пропускаем пустые строки

            // 3. АВТООПРЕДЕЛЕНИЕ РАЗДЕЛИТЕЛЯ (запятая или точка с запятой)
            String delimiter = line.contains(";") ? ";" : ",";
            
            String[] parts = line.split(delimiter);
            
            // 4. Очистка от кавычек и лишних пробелов (Excel любит их ставить)
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].replace("\"", "").trim();
            }

            if (parts.length >= 5) {
                try {
                    // 5. Обработка цены: меняем запятую на точку (1500,50 -> 1500.50)
                    String priceStr = parts[4].replace(",", ".");
                    
                    Train t = new Train(
                        Integer.parseInt(parts[0]),
                        parts[1],
                        parts[2],
                        parts[3],
                        Double.parseDouble(priceStr)
                    );
                    
                    // Если ID существует — обновляем, если нет — добавляем
                    if (index.containsKey(t.getId())) {
                        editTrain(t); 
                    } else {
                        addTrain(t);  
                    }
                } catch (Exception e) {
                    System.out.println("Пропущена строка (ошибка формата): " + line);
                }
            }
        }
    }
}
}