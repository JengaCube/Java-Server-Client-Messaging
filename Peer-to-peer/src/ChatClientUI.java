import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.Side;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.Executors;

public class ChatClientUI extends Application {
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String username;

    private TextArea chatArea;
    private TextField inputField;
    private ComboBox<String> whisperTarget;
    private Button sendButton;
    private Button quitButton;
    private Button membersButton;
    private Button emojiButton;
    private Button themeToggleButton;
    private boolean isDarkMode = false;
    private Scene scene;
    private VBox layout;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        askConnectionDetails(primaryStage);
    }

    private void askConnectionDetails(Stage stage) {
        Label title = new Label("Welcome to SMS Chat");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 10 0;");

        TextField ipField = new TextField("");
        TextField portField = new TextField("");
        TextField userField = new TextField();
        userField.setPromptText("Choose a unique username");

        Button connectButton = new Button("\uD83D\uDD17 Connect");
        connectButton.getStyleClass().add("button-send");

        VBox box = new VBox(10, title,
                new Label("Server IP:"), ipField,
                new Label("Port:"), portField,
                new Label("Username:"), userField,
                connectButton);

        box.setPrefWidth(300);
        box.getStyleClass().add("login-box");
        Scene loginScene = new Scene(box, 400, 350);
        loginScene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        connectButton.setOnAction(e -> {
            try {
                String ip = ipField.getText();
                int port = Integer.parseInt(portField.getText());
                username = userField.getText().trim();
                if (username.isEmpty()) return;
                socket = new Socket(ip, port);
                bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                bufferedWriter.write(username);
                bufferedWriter.newLine();
                bufferedWriter.flush();

                // Wait for server confirmation or rejection
                String response = bufferedReader.readLine();

                if (response != null && response.startsWith("❗ Username already taken")) {
                    showError(response);
                    socket.close(); // Disconnect from server
                    return; // Abort connection setup
                }


                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), box);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(event -> {
                    setupChatUI(primaryStage);
                    startMessageListener();
                });
                fadeOut.play();

            } catch (Exception ex) {
                showError("Connection failed: " + ex.getMessage());
            }
        });

        stage.setScene(loginScene);
        stage.setTitle("Chat Login");
        stage.show();
    }

    private void setupChatUI(Stage stage) {
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setPrefHeight(250);
        chatArea.getStyleClass().add("text-area");

        inputField = new TextField();
        inputField.setPromptText("Type a message...");
        inputField.setPrefWidth(300);

        whisperTarget = new ComboBox<>();
        whisperTarget.setPrefWidth(150);
        whisperTarget.getItems().add("Broadcast to All");
        whisperTarget.setValue("Broadcast to All");

        emojiButton = new Button("😊");
        emojiButton.setStyle("-fx-font-size: 16px;");
        emojiButton.setOnAction(e -> {
            ContextMenu emojiMenu = new ContextMenu();
            String[] emojis = {"😀", "😂", "🥲", "😎", "😍", "😢", "👍", "🎉"};
            for (String emoji : emojis) {
                MenuItem item = new MenuItem(emoji);
                item.setOnAction(ae -> inputField.appendText(emoji));
                emojiMenu.getItems().add(item);
            }
            emojiMenu.show(emojiButton, Side.TOP, 0, 0);
        });

        sendButton = new Button("\uD83D\uDCAC Send");
        sendButton.setPrefWidth(80);
        sendButton.getStyleClass().add("button-send");
        sendButton.setOnAction(e -> sendMessage());

        quitButton = new Button("\u274C Quit");
        quitButton.getStyleClass().add("button-quit");
        quitButton.setOnAction(e -> {
            sendRawMessage("/quit");
            closeEverything();
            Platform.exit();
            System.exit(0);
        });

        membersButton = new Button("\uD83D\uDC65 /members");
        membersButton.getStyleClass().add("button-members");
        membersButton.setOnAction(e -> sendRawMessage("/members"));

        themeToggleButton = new Button("\uD83C\uDF1C Dark Mode");
        themeToggleButton.getStyleClass().add("button-members");
        themeToggleButton.setOnAction(e -> toggleTheme());

        HBox controls = new HBox(10, whisperTarget, inputField, emojiButton, sendButton);
        controls.setStyle("-fx-padding: 5;");

        HBox footer = new HBox(10, membersButton, themeToggleButton, quitButton);
        footer.setStyle("-fx-padding: 5;");

        Label userLabel = new Label("Logged in as: " + username);
        userLabel.setStyle("-fx-font-weight: bold; -fx-padding: 5 0 5 0;");

        layout = new VBox(10, userLabel, chatArea, controls, footer);
        layout.getStyleClass().add("root");

        scene = new Scene(layout, 600, 400);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        stage.setScene(scene);
        stage.setTitle("Group Chat - " + username);
        stage.show();

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), layout);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        layout.setOpacity(0);
        fadeIn.play();

        stage.setOnCloseRequest(event -> {
            sendRawMessage("/quit");
            closeEverything();
            Platform.exit();
            System.exit(0);
        });
    }

private void toggleTheme() {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), layout);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> {
            scene.getStylesheets().clear();
            if (isDarkMode) {
                scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
                themeToggleButton.setText("\uD83C\uDF1C Dark Mode");
            } else {
                scene.getStylesheets().add(getClass().getResource("dark.css").toExternalForm());
                themeToggleButton.setText("\u2600 Light Mode");
            }
            isDarkMode = !isDarkMode;

            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), layout);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
        fadeOut.play();
    }

    private void sendMessage() {
        String message = inputField.getText().trim();
        String target = whisperTarget.getValue();
        if (message.isEmpty()) return;

        String timestamp = java.time.LocalTime.now().withNano(0).toString();

        if (target != null && !target.equals("Broadcast to All")) {
            sendRawMessage("/whisper " + target + " " + message);
            // Do NOT append locally — let server handle it

        } else {
            sendRawMessage(message);
            appendToChat("[" + timestamp + "] 🗣️ You: " + message);  // <--- Show your own message
        }

        inputField.clear();
    }

    private void sendRawMessage(String message) {
        try {
            bufferedWriter.write(message);
            bufferedWriter.newLine();
            bufferedWriter.flush();
        } catch (IOException e) {
            appendToChat("❌ Failed to send message.");
        }
    }

    private void startMessageListener() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String msg;
                while ((msg = bufferedReader.readLine()) != null) {

                    if (msg.equals("PING")) continue;

                    if (msg.startsWith("/whisper:add:")) {
                        String newUser = msg.substring("/whisper:add:".length()).trim();
                        Platform.runLater(() -> addUserToWhisperList(newUser));
                        continue;
                    }

                    if (msg.startsWith("/whisper:remove:")) {
                        String removedUser = msg.substring("/whisper:remove:".length()).trim();
                        Platform.runLater(() -> whisperTarget.getItems().remove(removedUser));
                        continue;
                    }

                    final String incoming = msg;
                    Platform.runLater(() -> {
                        appendToChat(incoming);
                        animateNewMessage();
                        updateWhisperList(incoming);
                    });
                }
            } catch (IOException e) {
                Platform.runLater(() -> appendToChat("🔌 Disconnected from server."));
            }
        });
    }

    private void animateNewMessage() {
        FadeTransition ft = new FadeTransition(Duration.millis(300), chatArea);
        ft.setFromValue(0.8);
        ft.setToValue(1);
        ft.play();
    }

    private void updateWhisperList(String msg) {
        if (msg.contains("👤 ")) {
            String name = msg.replaceAll(".*👤 ", "").split(" ")[0];
            addUserToWhisperList(name);
        }

        if (msg.contains(" has joined the chat")) {
            String name = msg.replaceAll(".*SERVER: ", "").replace(" has joined the chat", "").trim();
            addUserToWhisperList(name);
        }
    }

    private void addUserToWhisperList(String name) {
        if (!name.equals(username) && !whisperTarget.getItems().contains(name)) {
            whisperTarget.getItems().add(name);
        }
    }

    private void appendToChat(String message) {
        String timestamp = java.time.LocalTime.now().withNano(0).toString();
        chatArea.appendText("[" + timestamp + "] " + message + "\n");
    }

    private void closeEverything() {
        try {
            if (bufferedReader != null) bufferedReader.close();
            if (bufferedWriter != null) bufferedWriter.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
