package main.interactionmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import main.interactionmanager.gui.GUI;
import rise.core.utils.tecs.Behaviour;
import rise.core.utils.tecs.TECSClient;

/**
 *
 */
public class InteractionManager {

	public static final List<String> holder = new LinkedList<String>();

	private static final boolean USE_NAO = false;
	private static final boolean USE_TEST_QUESTION = false;

	private static final String HOST = "127.0.0.1";
	private static final int PORT = 1111;

	private static final String EXIT_COMMAND = "EXIT";

	private static Socket socket = null;
	private static PrintWriter out = null;
	private static BufferedReader in = null;
	private static Scanner questionReader = null;
	private static TECSClient tc = null;

	private static GUI gui = null;

	private static Logger logger = null;

	private static String[] confirmations = { "Je vroeg", "De vraag is", "Jouw vraag was" };
	private static String[] prompts = new String[] { "Wil je nog een vraag stellen?", "Heb je een vraag?" };
	private static String[] updates = new String[] { "Even nadenken.", "Even kijken" };
	private static String[] negativeResponses = new String[] { "Daar kon ik geen antwoord op vinden.",
			"Sorry, daar kon ik niets over vinden." };

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {
		logger = LoggerFactory.getLogger(InteractionManager.class);
		logger.info("NEW SESSION");

		initStreams();
		if (USE_NAO) {
			tc = new TECSClient("192.168.1.147", "TECSClient", 1234);
			tc.startListening();
			logger.info("Connected to TECS server");
		}
		if (USE_TEST_QUESTION) {
			sendTestQuestion();
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				gui = new GUI();
			}
		});

		while (true) {
			// Ask for a question
			String prompt = prompts[(int) (Math.random() * confirmations.length - 1)];
			if (USE_NAO) {
				tc.send(new Behaviour(1, "Propose", prompt));
				tc.send(new Behaviour(1, "StandHead", ""));
			}

			// Send input
			// question = questionReader.nextLine();

			synchronized (holder) {
				while (holder.isEmpty()) {
					try {
						holder.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			String question = holder.remove(0);
			if (question != null && question.equals(EXIT_COMMAND)) {
				break;
			}

			sendQuestion(question);
			// Return response
			String answer;
			try {
				answer = in.readLine();
				logger.info("Received answer: " + answer);
				gui.showAnswer(answer);
				if (USE_NAO) {
					tc.send(new Behaviour(1, "Propose", answer));
					tc.send(new Behaviour(1, "StandHead", ""));
				}
			} catch (IOException ex) {

			}
		}

		// Clean up on receiving exit command
		closeStreams();
		logger.info("END OF SESSION");
	}

	private static void showGUI() {
		new GUI();
	}

	private static void sendTestQuestion() {
		String question = "Wat is een computer";
		out.println(question);
		logger.info("Sending question (test): " + question);

		try {
			String answer = in.readLine();
			logger.info("Received answer (test): " + answer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void sendQuestion(String question) {
		out.println(question);
		logger.info("Sending question: " + question);

		String confirmation = confirmations[(int) (Math.random() * confirmations.length - 1)];
		if (USE_NAO) {
			tc.send(new Behaviour(1, "Me", confirmation + ": " + question));
			tc.send(new Behaviour(1, "State", ""));
			tc.send(new Behaviour(1, "StandHead", ""));
		}
	}

	private static void initStreams() {
		try {
			socket = new Socket(HOST, PORT);
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		} catch (IOException ex) {

		}

		if (out == null || in == null) {
			logger.error("Failed to initialize streams");
			// System.exit(1);
		}
	}

	private static void closeStreams() {
		questionReader.close();
		out.close();
		try {
			socket.close();
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (USE_NAO) {
			tc.disconnect();
		}
		logger.info("Closed streams");
	}
}