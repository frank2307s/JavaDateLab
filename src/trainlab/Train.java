package trainlab;

public class Train {
    private int id;
    private String trainNumber;
    private String destination;
    private String departureTime;
    private double price;

    public Train(int id, String trainNumber, String destination, String departureTime, double price) {
        this.id = id;
        this.trainNumber = trainNumber;
        this.destination = destination;
        this.departureTime = departureTime;
        this.price = price;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTrainNumber() { return trainNumber; }
    public String getDestination() { return destination; }
    public String getDepartureTime() { return departureTime; }
    public double getPrice() { return price; }
}