package application;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class Client extends Application implements Runnable {

	private Socket client;
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private boolean done = false;
	private boolean gameStarted = false;

	// needs to be same as server
	// screen dimensions and paddle/ball sizes
	private static final int WIDTH = 800;
	private static final int HEIGHT = 600;
	private static final int PADDLE_WIDTH = 10;
	private static final int PADDLE_HEIGHT = 100;
	private static final int BALL_SIZE = 15;

	private double ballX, ballY;
	private double player1Y, player2Y;

	//text fields, button, and score board
	private TextField nameField;
	private TextField ipField;
	private TextField portField;
	private Button connectButton;

	private ScoreBoard scoreBoard;

	// Keep track of scores (who it belongs to) coming in from server
	private boolean firstPlayerScore = true;

	// Client name
	private String playerName;

	//client thread
	@Override
	public void run() {

		try {
			client = new Socket(ipField.getText(), Integer.parseInt(portField.getText()));
			out = new ObjectOutputStream(client.getOutputStream());
			in = new ObjectInputStream(client.getInputStream());

			// send name to server
			out.writeObject(playerName);
			out.flush();
			
            showAlert(Alert.AlertType.INFORMATION, "Connection Status", "Connection to server successful!");

			Object message;
			while (!done && (message = in.readObject()) != null) {
				if (message instanceof Server.GameState) {
					// process game state updates
					Server.GameState gameState = (Server.GameState) message;
					Platform.runLater(() -> updateGameState(gameState));
				} else if (message instanceof Integer) {
					int i = Integer.parseInt(message.toString());

					// System.out.println("Integer received from server! " + i);

					if (firstPlayerScore) {
						this.scoreBoard.setP1Score(i);
						firstPlayerScore = false;
					} else {
						this.scoreBoard.setP2Score(i);
						firstPlayerScore = true;
					}

				} else if (message instanceof String) {
					if (((String) message).equals("StartGame")) {
						startGame();
					} else {
						System.out.println((String) message);
						scoreBoard.setP1Name((String) message);
					}
				}
			}

		} catch (NumberFormatException e) {
			showAlert(Alert.AlertType.ERROR, "Invalid Port", "Please enter a valid port number.");
		} catch (IOException e) {
			System.out.println("Failed to connect to server ip: " + ipField.getText() + " Port: " + portField.getText());
			showAlert(Alert.AlertType.ERROR, "Connection Error", "Failed to connect to the server.");
		} catch (Exception e) {
			if (!done) {
				e.printStackTrace();
			}
			shutdown();
		}
	}

	//update game state with the received data from server
	public void updateGameState(Server.GameState gameState) {
		this.ballX = gameState.ballX;
		this.ballY = gameState.ballY;
		this.player1Y = gameState.player1Y;
		this.player2Y = gameState.player2Y;
	}

	//run when starting game when the server signals
	private void startGame() {
		if (!gameStarted) {
			gameStarted = true;

			Platform.runLater(() -> {
				Stage primaryStage = new Stage();
				primaryStage.setTitle("Ping Pong Game - Client");

				Canvas canvas = new Canvas(WIDTH, HEIGHT);
				GraphicsContext gc = canvas.getGraphicsContext2D();

				Group root = new Group();
				root.getChildren().add(canvas);

				Scene scene = new Scene(root, WIDTH, HEIGHT);
				primaryStage.setScene(scene);
				primaryStage.show();

				
				//handle key press and release events
				//this is to create input where keypresses starts movement and release ends it 
				scene.setOnKeyPressed(this::handleKeyPress);
				scene.setOnKeyReleased(this::handleKeyRelease);

				//loop that draws what the server sends
				new AnimationTimer() {
					@Override
					public void handle(long now) {
						draw(gc);
					}
				}.start();

				primaryStage.setOnCloseRequest(event -> {
					shutdown();
					Platform.exit();
					System.exit(0);
				});
			});
		}
	}

	//javafx start
	@Override
	public void start(Stage primaryStage) {
		primaryStage.setTitle("Ping Pong Game - Client");

		Label nameLabel = new Label("Your Name:");
		nameField = new TextField("Client");

		Label ipLabel = new Label("Server IP:");
		ipField = new TextField("127.0.0.1"); // Set local IP as default

		Label portLabel = new Label("Server Port:");
		portField = new TextField("9999"); // Set uncommon 9999 port value as default

		scoreBoard = new ScoreBoard(); //Scoreboard shows up on screen

		connectButton = new Button("Connect");
		connectButton.setOnAction(event -> {
			if (nameField.getText().isEmpty() || ipField.getText().isEmpty() || portField.getText().isEmpty()) {
				showAlert(Alert.AlertType.ERROR, "Missing Information", "Please enter your name, server IP, and port.");
			} else {
				playerName = nameField.getText();
				scoreBoard.setP2Name(playerName);

				Thread clientThread = new Thread(this);
				clientThread.start();
				System.out.println("Attempting to connect to server ip: " + ipField.getText() + " Port: " + portField.getText());
			}
		});

		VBox vbox = new VBox(10, nameLabel, nameField, ipLabel, ipField, portLabel, portField, connectButton);
		vbox.setPadding(new Insets(10));

		Scene scene = new Scene(vbox, 300, 250);
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	//helper method for alerts
	private void showAlert(Alert.AlertType alertType, String title, String message) {
		Platform.runLater(() -> {
			Alert alert = new Alert(alertType);
			alert.setTitle(title);
			alert.setHeaderText(null);
			alert.setContentText(message);
			alert.showAndWait();
		});
	}

	//draw graphics
	private void draw(GraphicsContext gc) {
		gc.setFill(Color.BLACK);
		gc.fillRect(0, 0, WIDTH, HEIGHT);

		gc.setFill(Color.WHITE);
		gc.fillRect(0, player1Y, PADDLE_WIDTH, PADDLE_HEIGHT);
		gc.fillRect(WIDTH - PADDLE_WIDTH, player2Y, PADDLE_WIDTH, PADDLE_HEIGHT);

		gc.fillOval(ballX, ballY, BALL_SIZE, BALL_SIZE);

		//draw scoreboard on screen
		scoreBoard.draw(gc);
	}

	//handle pressing of key input
	private void handleKeyPress(KeyEvent event) {
		KeyCode code = event.getCode();

		if (code == KeyCode.DOWN) {
			try {
				sendInputToServer(0);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}

		if (code == KeyCode.UP) {
			try {
				sendInputToServer(2);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}
	}
	

	//Handle release of key inputs
	private void handleKeyRelease(KeyEvent event) {
		KeyCode code = event.getCode();

		if (code == KeyCode.DOWN) {
			try {
				sendInputToServer(1);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}

		if (code == KeyCode.UP) {
			try {
				sendInputToServer(3);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return;
		}
	}

	//send inputs to server for this player (player2/client) 
	private synchronized void sendInputToServer(int input) throws IOException {
		if (client != null && !client.isClosed() && out != null) {
			out.writeObject(input);
			out.flush();
		} else {
			System.out.println("Cannot send input to server: Socket is closed or output stream is null");
		}
	}

	//shutdown and close connection
	public void shutdown() {
		done = true;
		try {
			if (in != null)
				in.close();
			if (out != null)
				out.close();
			if (client != null && !client.isClosed())
				client.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}