import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private Gson gson = new Gson();
    private String botId;

    private Character[][] board;
    private Integer containerMaxCapacity;
    private List<Container> containers = List.of(new Container(), new Container(), new Container());

    private Map<BoardObjectType, List<BoardObject>> objects = new HashMap<>();

    private Map<BoardObjectType, List<BoardObject>> recyclingPoints = new HashMap<>();

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
            long startTime = System.nanoTime();

            do {
                int r = this.buffReader.read();
                if (r < 0 || r > 65535) {
                    throw new IllegalArgumentException("Invalid Char code: " + r);
                }
                c = (char) r;
                //println("char: " + c)
                message.append(c);
            } while (c != (char) 0);

            //TODO: do smth with message
            String replacedMessage = message.substring(4, message.length()).replaceAll("\\r\\n", "").replace("\0", "");
            JsonObject root = gson.fromJson(replacedMessage, JsonObject.class);

            if (root.get("bot_id") != null) {
                botId = root.get("bot_id").getAsString();
            } else if (root.get("gameBoard") != null) {
                JsonArray boardFromServer = root.get("gameBoard").getAsJsonArray();
                this.containerMaxCapacity = root.get("maxVol").getAsInt();

                List<List<Character>> board = new ArrayList<>();
                AtomicInteger i = new AtomicInteger();

                boardFromServer.forEach(jsonElement -> {
                    JsonArray boardElement = jsonElement.getAsJsonArray();
                    List<Character> characters = new ArrayList<>();
                    AtomicInteger j = new AtomicInteger();
                    boardElement.forEach(be -> {
                        char asCharacter = be.getAsJsonPrimitive().getAsCharacter();
                        if (asCharacter == 'P' || asCharacter == 'W' || asCharacter == 'M' || asCharacter == 'G' || asCharacter == 'E') {
                            BoardObjectType boardObjectType = BoardObjectType.valueOf(Character.toString(asCharacter));
                            recyclingPoints.computeIfAbsent(boardObjectType, k -> new ArrayList<>());
                            List<BoardObject> boarGameObj = recyclingPoints.get(boardObjectType);
                            boarGameObj.add(new BoardObject(i, j));
                            recyclingPoints.put(boardObjectType, boarGameObj);
                        }
                        j.getAndIncrement();
                        characters.add(asCharacter);
                    });

                    board.add(characters);
                    i.getAndIncrement();
                });
                this.board = board.stream()
                        .map(l -> l.toArray(new Character[0]))
                        .toArray(Character[][]::new);

                this.objects = gson.fromJson(root.get("objects"), new TypeToken<Map<BoardObjectType, List<BoardObject>>>() {
                }.getType());
                int positionX = root.get("row").getAsInt();
                int positionY = root.get("col").getAsInt();
                Pair<BoardObjectType, BoardObject, Integer> closestGarbage = getNextCellToClosestGarbage(positionX, positionY, null, objects);
                if (positionX == closestGarbage.second.row && positionY == closestGarbage.second.col) {
                    pickUpGarbage();
                } else {
                    decideMove(closestGarbage, positionX, positionY);
                }
            } else {
                Map<BoardObjectType, List<BoardObject>> currentObjets = gson.fromJson(root.get("objects"), new TypeToken<Map<BoardObjectType, List<BoardObject>>>() {
                }.getType());
                List<Container> containers = gson.fromJson(root.get("containers"), new TypeToken<List<Container>>() {
                }.getType());

                int positionX = root.get("row").getAsInt();
                int positionY = root.get("col").getAsInt();

                boolean messageSentPerRound = false;
                if (containers != null) {
                    if (containers.size() == 3) {
                        Pair<BoardObjectType, BoardObject, Integer> closestRecyclingPoint = getNextCellToClosestGarbage(positionX, positionY, containers.stream().map(Container::getType).collect(Collectors.toList()), recyclingPoints);
                        if (positionX == closestRecyclingPoint.second.row && positionY == closestRecyclingPoint.second.col) {
                            messageSentPerRound = true;
                            dropGarbage();
                        } else {
                            messageSentPerRound = true;
                            decideMove(closestRecyclingPoint, positionX, positionY);
                        }
                    }
                }
                if (!messageSentPerRound) {
                    /// if we are on the object pick it up
                    Pair<BoardObjectType, BoardObject, Integer> closestGarbage = getNextCellToClosestGarbage(positionX, positionY, null, currentObjets);
                    if (positionX == closestGarbage.second.row && positionY == closestGarbage.second.col) {
                        pickUpGarbage();
                    } else {
                        decideMove(closestGarbage, positionX, positionY);
                    }
                }
            }

            long endTime = System.nanoTime();
            long executionTime = endTime - startTime;
            System.out.println("Execution time  " + executionTime);
            message = new StringBuilder();
        }
    }

    private void dropGarbage() throws IOException {
        Act drop = new Act();
        drop.action = "drop";
        drop.bot_id = this.botId;

        //drop garbage
        sendMessage(gson.toJson(drop));
    }

    private void decideMove(Pair<BoardObjectType, BoardObject, Integer> closestGarbage, int positionX, int positionY) throws IOException {

        Move move = new Move();
        if (closestGarbage.second.col < positionY) {
            move.move = "left";
        } else if (closestGarbage.second.col > positionY) {
            move.move = "right";
        } else if (closestGarbage.second.row < positionX) {
            move.move = "up";
        } else {
            move.move = "down";
        }
        move.bot_id = botId;
        move.speed = 1;
        sendMessage(gson.toJson(move));
    }

    public Pair<BoardObjectType, BoardObject, Integer> getNextCellToClosestGarbage(int row, int col, List<BoardObjectType> boardObjectTypes, Map<BoardObjectType, List<BoardObject>> currentObjects) {
        int MIN = 100;
        int[] start = {row, col};
        BoardObject nextStep = null;
        BoardObjectType closestGarbageType = null;
        Integer steps = 0;
        if (boardObjectTypes != null) {
            for (BoardObjectType currentBoardType : boardObjectTypes) {
                if (currentObjects.containsKey(currentBoardType)) {
                    for (BoardObject boardObject : currentObjects.get(currentBoardType)) {
                        int[] end = {boardObject.row, boardObject.col};
                        List<Bfs.Cell> path = Bfs.shortestPath(this.board, start, end);
                        if (path.size() < MIN && path.size() > 1) {
                            closestGarbageType = currentBoardType;
                            nextStep = new BoardObject(path.get(1).x, path.get(1).y);
                            steps = path.size() - 1;
                            MIN = path.size();
                        } else if (path.size() == 1) {
                            closestGarbageType = currentBoardType;
                            nextStep = new BoardObject(path.get(0).x, path.get(0).y);
                            steps = 0;
                            MIN = 1;
                        }
                    }
                }
            }
        } else {
            for (BoardObjectType boardObjectType1 : BoardObjectType.values()) {
                if (boardObjectType1.isGarbage() && currentObjects.containsKey(boardObjectType1)) {
                    for (BoardObject boardObject : currentObjects.get(boardObjectType1)) {
                        int[] end = {boardObject.row, boardObject.col};
                        List<Bfs.Cell> path = Bfs.shortestPath(this.board, start, end);
                        if (path.size() < MIN && path.size() > 1) {
                            closestGarbageType = boardObjectType1;
                            nextStep = new BoardObject(path.get(1).x, path.get(1).y);
                            steps = path.size() - 1;
                            MIN = path.size();
                        } else if (path.size() == 1) {
                            closestGarbageType = boardObjectType1;
                            nextStep = new BoardObject(path.get(0).x, path.get(0).y);
                            steps = 0;
                            MIN = 1;
                        }
                    }
                }
            }
        }
        return new Pair<>(closestGarbageType, nextStep, steps);
    }


    private void pickUpGarbage() throws IOException {
        Act pickUp = new Act();
        pickUp.action = "pick";
        pickUp.bot_id = this.botId;
        //pick up garbage
        sendMessage(gson.toJson(pickUp));
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
