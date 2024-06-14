package application;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class ScoreBoard {
    private int player1Score;
    private int player2Score;
    
    //positions of text on the screen
    private static final int SCORE_POSITION_X1 = 200;
    private static final int SCORE_POSITION_X2 = 600;
    private static final int SCORE_POSITION_Y = 50;
    
    //name of player 1 and 2
    private String p1Name;
    private String p2Name;

    public ScoreBoard() {
        this.player1Score = 0;
        this.player2Score = 0;
    }

    public void increasePlayer1Score() {
        player1Score++;
    }

    public void increasePlayer2Score() {
        player2Score++;
    }
    
    public void setP1Score(int p1) {
    	player1Score = p1;
    }
    
    public void setP2Score(int p2) {
    	player2Score = p2;
    }
    
    public int getScoreP1() {
    	return player1Score;
    }
    
    public int getScoreP2() {
    	return player2Score;
    }
    
    public void setP1Name(String s) {
    	p1Name = s;
    }
    
    public void setP2Name(String s) {
    	p2Name = s;
    }

    //call to draw/update scoreboard on screen
    public void draw(GraphicsContext gc) {
        gc.setFill(Color.WHITE);
        gc.fillText(p1Name + ": " + player1Score, SCORE_POSITION_X1, SCORE_POSITION_Y);
        gc.fillText(p2Name + ": " + player2Score, SCORE_POSITION_X2, SCORE_POSITION_Y);
    }
}
