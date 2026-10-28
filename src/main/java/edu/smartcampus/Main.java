package edu.smartcampus;

public final class Main {
    private Main() { }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        try (CampusWebServer server = new CampusWebServer(port)) {
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            server.start();
            System.out.println("Smart Campus website is running at http://localhost:" + port);
            System.out.println("Press Ctrl+C to stop the server.");
            new java.util.concurrent.CountDownLatch(1).await();
        }
    }
}
