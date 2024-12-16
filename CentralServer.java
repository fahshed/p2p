import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class CentralServer {

    private static CopyOnWriteArrayList<PeerInfo> peerList = new CopyOnWriteArrayList<>();
    private static boolean isRunning = true;

    public static void main(String[] args) {
        System.out.println("The Server is on!");
        ExecutorService executor = Executors.newCachedThreadPool();

        new Thread(() -> {
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            String command;
            try {
                while (isRunning && (command = consoleReader.readLine()) != null) {
                    if (command.equalsIgnoreCase("members")) {
                        showMembers();
                    } else if (command.equalsIgnoreCase("quit")) {
                        isRunning = false;
                        consoleReader.close();
                        System.exit(0);
                    } else {
                        System.out.println("Unknown command.");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        try (ServerSocket serverSocket = new ServerSocket(5077)) {
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                executor.execute(new PeerHandler(clientSocket));
            }
        } catch (IOException e) {
            if (isRunning) {
                e.printStackTrace();
            }
        } finally {
            executor.shutdown();
        }
    }

    private static void showMembers() {
        if (peerList.isEmpty()) {
            System.out.println("No peers connected.");
        } else {
            System.out.println("Current peers:");
            for (PeerInfo peer : peerList) {
                System.out.println(peer.hostname + "/" + peer.ipAddress);
            }
        }
    }

    static class PeerHandler implements Runnable {
        private Socket socket;

        public PeerHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                String message = in.readLine();

                String[] parts = message.split(":");
                String peerHostname = parts[1];
                int peerPort = Integer.parseInt(parts[2]);
                String peerIpAddress = socket.getInetAddress().getHostAddress();

                if (message.startsWith("REGISTER")) {
                    PeerInfo newPeer = new PeerInfo(peerHostname, peerIpAddress, peerPort);

                    if (peerList.size() > 0) {
                        PeerInfo existingPeer;
                        existingPeer = peerList.get(new Random().nextInt(peerList.size()));
                        peerList.add(newPeer);
                        out.println(existingPeer.hostname + ":" + existingPeer.ipAddress + ":" + existingPeer.port);
                    } else {
                        peerList.add(newPeer);
                        out.println("CONFIRM");
                    }
                } else if (message.startsWith("DISCONNECT")) {
                    peerList.removeIf(p -> p.ipAddress.equals(peerIpAddress) && p.port == peerPort);
                    System.out.println("Peer " + peerHostname + "/" + peerIpAddress + " disconnected!");
                }

                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class PeerInfo {
        String hostname;
        String ipAddress;
        int port;

        public PeerInfo(String hostname, String ipAddress, int port) {
            this.hostname = hostname;
            this.ipAddress = ipAddress;
            this.port = port;
        }
    }
}
