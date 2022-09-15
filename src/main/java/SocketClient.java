import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.BoardObject;
import model.Container;
import model.Move;
import model.MoveType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SocketClient {

    public static void main(String[] args) {
        String address = "127.0.0.1";
        int port = 31415;
        String teamName = "Runtime Terror";
        MyClient client = new MyClient(address, port);
        new Thread(client).start();
        client.register(teamName);
    }
}


class MyClient implements Runnable {
    private final Socket connection;
    private boolean connected = true;
    private final BufferedReader buffReader;
    private final OutputStream writer;
    private String botId;


    private Character[][] board;
    private Integer containerMaxCapacity;
    private List<Container> containers = List.of(new Container(), new Container(), new Container());

    private Map<String, List<BoardObject>> objects = new HashMap<>();

    public MyClient(String address, int port) {
        this.connection = initConnection(address, port);
        this.buffReader = initReader();
        this.writer = initWriter();
    }

    @Override
    public void run() {
        try {
            read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void register(String teamName) {
        String registerMsg = "register: " + teamName;
        System.out.println(registerMsg);
        registerMsg = "{ \"get_team_id_for\" :\"" + teamName + "\"}";

        try {
            this.sendMessage(registerMsg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) throws IOException {
        System.out.println("sendingMessage: " + message);
        int msgLen = message.length();
        String hex = String.format("%04X", msgLen);
        String fullMsg = hex + message + "\0";

        byte[] byteArray = fullMsg.getBytes(StandardCharsets.UTF_8);
        writer.write(byteArray);
    }

    private void read() throws IOException {
        StringBuilder message = new StringBuilder();
        char c;
        while (true) {
            do {
                int r = this.buffReader.read();
                if (r < 0 || r > 65535) {
                    throw new IllegalArgumentException("Invalid Char code: " + r);
                }
                c = (char) r;
                //println("char: " + c)
                message.append(c);
            } while (c != (char) 0);
            System.out.println("Message received: " + message);

            //TODO: do smth with message
            System.out.println();
            Gson gson = new Gson();
            String replacedMessage = message.substring(4, message.length()).replaceAll("\\r\\n", "").replace("\0", "");
            JsonObject root = gson.fromJson(replacedMessage, JsonObject.class);
            System.out.println(root);
            if (root.get("bot_id") != null) {
                botId = root.get("bot_id").getAsString();
            } else if (root.get("gameBoard") != null) {
                JsonArray boardFromServer = root.get("gameBoard").getAsJsonArray();
                this.containerMaxCapacity = root.get("maxVol").getAsInt();
                containers.stream().forEach(container -> container.setRemainingSpace(this.containerMaxCapacity));

                List<List<Character>> board = new ArrayList<>();
                boardFromServer.forEach(jsonElement -> {
                    JsonArray boardElement = jsonElement.getAsJsonArray();
                    List<Character> characters = new ArrayList<>();
                    boardElement.forEach(be ->
                            characters.add(be.getAsJsonPrimitive().getAsCharacter()));

                    board.add(characters);
                });
                this.board = board.stream()
                        .map(l -> l.toArray(new Character[0]))
                        .toArray(Character[][]::new);

                this.objects = gson.fromJson(root.get("objects"), new TypeToken<Map<String, List<BoardObject>>>() {
                }.getType());

                System.out.println(board);
            } else {
                sendMessage(gson.toJson(decideMove()));
            }

            message = new StringBuilder();
        }
    }

    private Move decideMove() {
        Move move = new Move();
        move.bot_id = botId;
        move.move = MoveType.randomMove().name();
        move.speed = 1;
        return move;
    }

    public void close() throws IOException {
        System.out.println("closing connection");
        this.connected = false;
        this.connection.close();
    }

    private Socket initConnection(String address, int port) {
        try {
            System.out.println("Connected to server at " + address + " on port " + port);
            return new Socket(address, port);
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private OutputStream initWriter() {
        try {
            return this.connection.getOutputStream();
        } catch (IOException ex) {
            return null;
        }
    }

    private BufferedReader initReader() {
        try {
            return new BufferedReader(new InputStreamReader(this.connection.getInputStream()));
        } catch (IOException ex) {
            return null;
        }
    }
}
