import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Peer {

    private static String centralServerIP;
    private static int centralServerPort = 5077;
    private static int hostPort;
    private static String hostname;
    private static CopyOnWriteArrayList<NeighborInfo> neighbors = new CopyOnWriteArrayList<>();
    private static boolean isRunning = true;
    private static ExecutorService executor = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        try {
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Enter Server IP: ");
            centralServerIP = consoleReader.readLine();
            hostname = InetAddress.getLocalHost().getHostName();
            hostPort = new Random().nextInt(10000) + 10000;

            if (registerWithCentralServer()) {
                ServerSocket serverSocket = new ServerSocket(hostPort);
                executor.execute(() -> acceptConnections(serverSocket));
                handleCommands();
            } else {
                System.out.println("Server down. Can't connect.");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void acceptConnections(ServerSocket serverSocket) {
        while (isRunning) {
            try {
                Socket socket = serverSocket.accept();
                executor.execute(new NeighborHandler(socket));
            } catch (IOException e) {
                if (isRunning) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static boolean registerWithCentralServer() {
        try (Socket socket = new Socket(centralServerIP, centralServerPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("REGISTER:" + hostname + ":" + hostPort);

            String response = in.readLine();
            if (response.equals("CONFIRM")) {
                System.out.println("No existing peers. Starting new P2P network.");
            } else {
                String[] parts = response.split(":");
                String peerHostname = parts[0];
                String peerIp = parts[1];
                int peerPort = Integer.parseInt(parts[2]);
                connectToPeer(peerHostname, peerIp, peerPort);
            }

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void connectToPeer(String peerHostname, String peerIP, int peerPort) {
        try {
            Socket socket = new Socket(peerIP, peerPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("REQUEST_CONNECT:" + hostname + ":" + hostPort);

            String response = in.readLine();
            if (response.equals("ACCEPT")) {
                neighbors.add(new NeighborInfo(peerHostname, peerIP, peerPort));
                System.out.println("Connected to peer: " + peerHostname);
            } else if (response.startsWith("REDIRECT:")) {
                String[] parts = response.split(":");
                String newPeerHostname = parts[1];
                String newPeerIP = parts[2];
                int newPeerPort = Integer.parseInt(parts[3]);
                socket.close();
                connectToPeer(newPeerHostname, newPeerIP, newPeerPort);
            }

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleCommands() {
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
        String command;
        try {
            while (isRunning && (command = consoleReader.readLine()) != null) {
                if (command.equalsIgnoreCase("neighbors")) {
                    showNeighbors();
                } else if (command.equalsIgnoreCase("quit")) {
                    isRunning = false;
                    consoleReader.close();
                    notifyServer();
                    notifyNeighbors();
                    executor.shutdownNow();
                    System.exit(0);
                } else {
                    System.out.println("Unknown command.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void showNeighbors() {
        if (neighbors.isEmpty()) {
            System.out.println("No neighbors connected.");
        } else {
            System.out.println("Neighbors:");
            for (NeighborInfo neighbor : neighbors) {
                System.out.println(neighbor.hostname + "/" + neighbor.ipAddress);
            }
        }
    }

    private static void notifyServer() {
        try {
            Socket socket = new Socket(centralServerIP, centralServerPort);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("DISCONNECT:" + hostname + ":" + hostPort);
            socket.close();
        } catch (IOException e) {
            System.out.println("Server down. Can't reach.");
        }
    }

    private static void notifyNeighbors() {
        for (NeighborInfo neighbor : neighbors) {
            try {
                Socket socket = new Socket(neighbor.ipAddress, neighbor.port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println("DISCONNECT:" + hostname + ":" + hostPort);
                socket.close();
            } catch (IOException e) {
                // Ignore exceptions on disconnect
            }
        }
    }

    static class NeighborHandler implements Runnable {
        private Socket socket;

        public NeighborHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                String remoteIP = socket.getInetAddress().getHostAddress();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                String message = in.readLine();
                String[] parts = message.split(":");
                String remoteHostname = parts[1];
                int remotePort = Integer.parseInt(parts[2]);

                if (message.startsWith("REQUEST_CONNECT:")) {
                    if (neighbors.size() < 3) {
                        neighbors.add(new NeighborInfo(remoteHostname, remoteIP, remotePort));
                        out.println("ACCEPT");
                        System.out.println("Connected to new neighbor: " + remoteHostname + "/" + remoteIP);
                    } else {
                        NeighborInfo nextNeighbor = neighbors.iterator().next();
                        out.println("REDIRECT:" + nextNeighbor.hostname + ":" + nextNeighbor.ipAddress + ":"
                                + nextNeighbor.port);
                    }
                } else if (message.startsWith("DISCONNECT:")) {
                    neighbors.removeIf(n -> n.ipAddress.equals(remoteIP) && n.port == remotePort);
                    System.out.println("Peer " + remoteHostname + "/" + remoteIP + " disconnected!");
                }

                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class NeighborInfo {
        String hostname;
        String ipAddress;
        int port;

        public NeighborInfo(String hostname, String ipAddress, int port) {
            this.hostname = hostname;
            this.ipAddress = ipAddress;
            this.port = port;
        }
    }
}
