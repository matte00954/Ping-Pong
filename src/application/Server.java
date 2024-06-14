package application;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class Server extends Application implements Runnable {

	private List<ConnectionHandler> connections;
		
	private ServerSocket serverSocket;
	private boolean done;
	private ExecutorService threadPool;
	private int port;
	private boolean player2Connected = false;
	private Label player2StatusLabel;
	private String playerName;
	private Label ipLabel;
	private TextField portField;
	private TextField nameField;
	private Button startButton;
	private Button startGameButton;

	//Screen
	private static final int WIDTH = 800;
	private static final int HEIGHT = 600;
	//paddle
	private static final int PADDLE_WIDTH = 10;
	private static final int PADDLE_HEIGHT = 100;
	private static final double PADDLE_SPEED = 5.0;
	
	private static final int BALL_SIZE = 15;

	//game state variables
	private double ballX, ballY, ballXSpeed, ballYSpeed;
	private double player1Y, player2Y;
	private Set<KeyCode> activeKeys;

	//input from client 
	private boolean clientPressedUp;
	private boolean clientPressedDown;
	private GraphicsContext gc;
	
	private ScoreBoard scoreBoard;

	public Server() {
		connections = new CopyOnWriteArrayList<>();
		done = false;
		scoreBoard = new ScoreBoard();
	}

	//main loop to handle connections
	@Override
	public void run() {
		try {
			serverSocket = new ServerSocket(port);
			threadPool = Executors.newCachedThreadPool();
			System.out.println("Server started at port " + serverSocket.getLocalPort());

			while (!done) {
				Socket client = serverSocket.accept();
				
				//only allow one player to connect
				if (!player2Connected) {
					player2Connected = true;
					Platform.runLater(() -> player2StatusLabel.setText("Player 2: Connected"));

					ConnectionHandler handler = new ConnectionHandler(client);
					connections.add(handler);
					threadPool.execute(handler);
				} else {
					client.close();
				}
			}
		} catch (IOException e) {
			System.err.println("Error initializing server: " + e.getMessage());
			shutdown();
		}
	}

	//broadcast to connections (only one allowed)
	public void broadcast(Object message) {
		for (ConnectionHandler ch : connections) {
			if (ch != null) {
				ch.sendMessage(message);
			}
		}
	}

	public void shutdown() {
		try {
			done = true;
			threadPool.shutdown();
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
			}
			for (ConnectionHandler ch : connections) {
				ch.shutdown();
			}
		} catch (IOException e) {
			System.err.println("Error during shutdown: " + e.getMessage());
		}
	}

	//javafx start
	@Override
	public void start(Stage primaryStage) {
		primaryStage.setTitle("Ping Pong Game - Server");

		ipLabel = new Label();
		try {
			ipLabel.setText("Server IP: " + InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			ipLabel.setText("Server IP: Unknown");
		}

		Label portLabel = new Label("Port:");
		portField = new TextField("9999");

		Label nameLabel = new Label("Your Name:");
		nameField = new TextField("Server");

		player2StatusLabel = new Label("Player 2: Not Connected");

		startButton = new Button("Start Server");
		startButton.setOnAction(event -> {
			try {
				int inputPort = Integer.parseInt(portField.getText());
				if (inputPort < 1 || inputPort > 65535) {
					showAlert(AlertType.ERROR, "Invalid Port", "Port number must be between 1 and 65535.");
				} else if (!isPortAvailable(inputPort)) {
					showAlert(AlertType.ERROR, "Port Unavailable", "Port " + inputPort + " is already in use.");
				} else {
					port = inputPort;
					playerName = nameField.getText();
					scoreBoard.setP1Name(playerName);
					if (playerName.isEmpty()) {
						showAlert(AlertType.ERROR, "Invalid Name", "Please enter your name.");
						return;
					}
					Thread serverThread = new Thread(this);
					serverThread.start();

					showAlert(AlertType.INFORMATION, "Server has started on port " + inputPort + " and ip " + InetAddress.getLocalHost().getHostAddress() ,
							"Player 2 is now able to connect to the server.");
					startButton.setDisable(true);
					ipLabel.setText("Server IP: " + InetAddress.getLocalHost().getHostAddress() + " - Server Started");
				}
			} catch (NumberFormatException | UnknownHostException e) {
				showAlert(AlertType.ERROR, "Error", e.getMessage());
			}
		});

		startGameButton = new Button("Start Game");

		startGameButton.setOnAction(event -> {
			if (player2Connected) {
				//send "StartGame" signal to connected client
				broadcast("StartGame");
				showGameWindow(primaryStage);
			} else {
				showAlert(AlertType.ERROR, "Player 2 Not Connected!", "Game is starting without player 2.");
				showGameWindow(primaryStage);
			}
		});
		player2StatusLabel.textProperty().addListener((observable, oldValue, newValue) -> {
			if (player2Connected) {
				startGameButton.setDisable(false);
			}
		});

		VBox vbox = new VBox(10, ipLabel, portLabel, portField, nameLabel, nameField, player2StatusLabel, startButton,
				startGameButton);
		vbox.setPadding(new Insets(10));

		Scene scene = new Scene(vbox, 300, 300);
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	//check if port is ok
	private boolean isPortAvailable(int port) {
		try (ServerSocket testSocket = new ServerSocket(port)) {
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	//help method to display alerts
	private void showAlert(AlertType alertType, String title, String message) {
		Alert alert = new Alert(alertType);
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.setContentText(message);
		alert.showAndWait();
	}

	//display game
	private void showGameWindow(Stage primaryStage) {
		Canvas canvas = new Canvas(WIDTH, HEIGHT);
		gc = canvas.getGraphicsContext2D();

		Group root = new Group();
		root.getChildren().add(canvas);

		Scene scene = new Scene(root, WIDTH, HEIGHT);
		primaryStage.setScene(scene);
		primaryStage.show();

		initializeGame();

		activeKeys = ConcurrentHashMap.newKeySet();
		scene.setOnKeyPressed(this::handleKeyPress);
		scene.setOnKeyReleased(this::handleKeyRelease);
		
		//limit frames per second (fps) in order to prevent very high fps issues
		final long frameInterval = 100000000 / 60; //ns
	    new AnimationTimer() {
	        private long lastUpdate = 0;

	        @Override
	        public void handle(long now) {
	            if (now - lastUpdate >= frameInterval) {
	                updateGame();
	                draw(gc);
	                lastUpdate = now;
	            }
	        }
	    }.start();

	    //old implementation
	    //can cause very high framerates
		/*
		new AnimationTimer() {
			@Override
			public void handle(long now) {
				updateGame();
				draw(gc);
			}
		}.start();*/

		primaryStage.setOnCloseRequest(event -> {
			shutdown();
			Platform.exit();
			System.exit(0);
		});
	}

	private void initializeGame() {
		ballX = WIDTH / 2 - BALL_SIZE / 2;
		ballY = HEIGHT / 2 - BALL_SIZE / 2;
		ballXSpeed = 2;
		ballYSpeed = 2;
		player1Y = HEIGHT / 2 - PADDLE_HEIGHT / 2;
		player2Y = HEIGHT / 2 - PADDLE_HEIGHT / 2;
	}

	//start movement when key is pressed
	private void handleKeyPress(KeyEvent event) {
		activeKeys.add(event.getCode());
	}

	//end when key is released
	private void handleKeyRelease(KeyEvent event) {
		activeKeys.remove(event.getCode());
	}

	//update game state
	private void updateGame() {
		if (activeKeys.contains(KeyCode.W)) {
			player1Y -= PADDLE_SPEED;
			if (player1Y < 0) {
				player1Y = 0;
			}
		}

		if (activeKeys.contains(KeyCode.S)) {
			player1Y += PADDLE_SPEED;
			if (player1Y > HEIGHT - PADDLE_HEIGHT) {
				player1Y = HEIGHT - PADDLE_HEIGHT;
			}
		}

		if (clientPressedUp) {
			player2Y -= PADDLE_SPEED;
			if (player2Y < 0) {
				player2Y = 0;
			}
		}

		if (clientPressedDown) {
			player2Y += PADDLE_SPEED;
			if (player2Y > HEIGHT - PADDLE_HEIGHT) {
				player2Y = HEIGHT - PADDLE_HEIGHT;
			}
		}

		//ball movement
		ballX += ballXSpeed;
		ballY += ballYSpeed;

		//collision with top and bottom walls
		if (ballY <= 0 || ballY >= HEIGHT - BALL_SIZE) {
			ballYSpeed = -ballYSpeed;
		}

		//ball collision with paddles
		if ((ballX <= PADDLE_WIDTH && ballY + BALL_SIZE >= player1Y && ballY <= player1Y + PADDLE_HEIGHT)
				|| (ballX >= WIDTH - PADDLE_WIDTH - BALL_SIZE && ballY + BALL_SIZE >= player2Y
						&& ballY <= player2Y + PADDLE_HEIGHT)) {
			ballXSpeed = -ballXSpeed;
		}

        if (ballX <= 0) {
        	
            scoreBoard.increasePlayer2Score();

            broadcast(scoreBoard.getScoreP1());
            broadcast(scoreBoard.getScoreP2());
            
            initializeGame();
        } else if (ballX >= WIDTH) {
        	
            scoreBoard.increasePlayer1Score();

            broadcast(scoreBoard.getScoreP1());
            broadcast(scoreBoard.getScoreP2());
            
            initializeGame();
        }

		// send game state to client
		GameState gameState = new GameState(ballX, ballY, player1Y, player2Y);
		broadcast(gameState);
	}

	//draw game 
	private void draw(GraphicsContext gc) {
		gc.setFill(Color.BLACK);
		gc.fillRect(0, 0, WIDTH, HEIGHT);

		gc.setFill(Color.WHITE);
		gc.fillRect(0, player1Y, PADDLE_WIDTH, PADDLE_HEIGHT);
		gc.fillRect(WIDTH - PADDLE_WIDTH, player2Y, PADDLE_WIDTH, PADDLE_HEIGHT);

		gc.fillOval(ballX, ballY, BALL_SIZE, BALL_SIZE);
		
		scoreBoard.draw(gc);
	}
	

	//handle connection from client
	class ConnectionHandler implements Runnable {
		private Socket client;
		private ObjectInputStream in;
		private ObjectOutputStream out;

		public ConnectionHandler(Socket client) {
			this.client = client;
		}

		//thread
		@Override
		public void run() {
			try {
				out = new ObjectOutputStream(client.getOutputStream());
				in = new ObjectInputStream(client.getInputStream());
				
				//send name (server player) to client
				scoreBoard.setP1Name(playerName);
				broadcast(playerName);

				Object message;
				while ((message = in.readObject()) != null) {
					if (message instanceof Integer) {
						int val = (Integer) message;

						if (val == 0) {
							clientPressedDown = true;
						} else if (val == 1) {
							clientPressedDown = false;
						}

						if (val == 2) {
							clientPressedUp = true;
						} else if (val == 3) {
							clientPressedUp = false;
						}
					}
					else {
						String s = (String) message;
						scoreBoard.setP2Name(s);
					}
				}
			} catch (ClassNotFoundException | IOException e) {
				System.err.println("Connection error: " + e.getMessage());
			} finally {
				shutdown();
			}
		}

		public void sendMessage(Object message) {
			try {
				if (out != null) {
					out.writeObject(message);
					out.flush();
				}
			} catch (IOException e) {
				System.err.println("Error sending message: " + e.getMessage());
				shutdown();
			}
		}

		public void shutdown() {
			try {
				if (in != null) {
					in.close();
				}
				if (out != null) {
					out.close();
				}
				if (client != null && !client.isClosed()) {
					client.close();
				}
			} catch (IOException e) {
				System.err.println("Error during connection shutdown: " + e.getMessage());
			} finally {
				connections.remove(this);
			}
		}
	}

	//state of the game
	static class GameState implements Serializable {
		private static final long serialVersionUID = 1L;
		double ballX, ballY;
		double player1Y, player2Y;

		public GameState(double ballX, double ballY, double player1Y, double player2Y) {
			this.ballX = ballX;
			this.ballY = ballY;
			this.player1Y = player1Y;
			this.player2Y = player2Y;
		}

		public void updateGameState(GameState gameState) {
			this.player1Y = gameState.player1Y;
			this.player2Y = gameState.player2Y;
			this.ballX = gameState.ballX;
			this.ballY = gameState.ballY;
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}