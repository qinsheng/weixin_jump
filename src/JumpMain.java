import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class JumpMain {

	private static BufferedImage IMAGE = null;

	private static JPanel PANEL = null;

	private static int WIDTH = 0;
	private static final int HEIGHT = 720;

	private static Point P1 = null;
	private static Point P3 = null;

	private static String STATE = "CLICK";
	private static long JUMP = 0;

	private static final Object LOCK = new Object();
	private static volatile boolean RUNNING = true;

	public static void main(String[] args) {

		new Thread() {
			@Override
			public void run() {
				while (RUNNING) {

					if (JUMP > 0) {
						jump(JUMP);
					}

					String filepath = screencap();
					try {
						File file = new File(filepath);
						IMAGE = ImageIO.read(file);
						file.delete();
						if (IMAGE != null) {
							WIDTH = IMAGE.getWidth() * HEIGHT / IMAGE.getHeight();
							IMAGE = scaleImage(IMAGE, WIDTH, HEIGHT);
						}
					} catch (IOException e) {
						IMAGE = null;
					}

					if (IMAGE == null) {
						tsleep(1000);
						continue;
					}

					if (!RUNNING) {
						break;
					}

					PANEL.repaint();

					parse();

					if (P1 != null && P3 != null) {
						compute();
						continue;
					}
					
					STATE = "CLICK";

					synchronized (LOCK) {
						try {
							LOCK.wait();
						} catch (InterruptedException e) {
						}
					}
				}
			}
		}.start();

		EventQueue.invokeLater(new Runnable() {

			@Override
			public void run() {
				JFrame frame = new JFrame();
				frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
				frame.setSize(480, HEIGHT);
				frame.setResizable(false);
				frame.setUndecorated(true);
				frame.addWindowListener(new WindowListener() {

					@Override
					public void windowOpened(WindowEvent e) {
					}

					@Override
					public void windowIconified(WindowEvent e) {
					}

					@Override
					public void windowDeiconified(WindowEvent e) {
					}

					@Override
					public void windowDeactivated(WindowEvent e) {
					}

					@Override
					public void windowClosing(WindowEvent e) {
						RUNNING = false;

						synchronized (LOCK) {
							LOCK.notifyAll();
						}
					}

					@Override
					public void windowClosed(WindowEvent e) {
					}

					@Override
					public void windowActivated(WindowEvent e) {
					}
				});
				PANEL = new JPanel() {

					private static final long serialVersionUID = -2219958771941230090L;

					@Override
					public void paintComponent(Graphics g) {
						BufferedImage image = IMAGE;
						if (image != null) {
							g.drawImage(image, 0, 0, null);
						}
					}
				};
				frame.add(PANEL);
				frame.setVisible(true);

				PANEL.addMouseListener(new MouseListener() {

					@Override
					public void mouseReleased(MouseEvent e) {
					}

					@Override
					public void mousePressed(MouseEvent e) {
					}

					@Override
					public void mouseExited(MouseEvent e) {
					}

					@Override
					public void mouseEntered(MouseEvent e) {
					}

					@Override
					public void mouseClicked(MouseEvent e) {
						if (!"CLICK".equals(STATE)) {
							return;
						}

						System.out.println(e.getPoint());
						Color color = new Color(IMAGE.getRGB(e.getPoint().x, e.getPoint().y), true);
						System.out.println(color);

						if (e.getButton() == 1) {
							P1 = new Point();
							P1.x = e.getPoint().x;
							P1.y = e.getPoint().y;

							drawPoint(P1.x, P1.y);
							PANEL.repaint();
						} else if (e.getButton() == 3) {
							P3 = new Point();
							P3.x = e.getPoint().x;
							P3.y = e.getPoint().y;

							drawPoint(P3.x, P3.y);
							PANEL.repaint();
						}

						if (P1 != null && P3 != null) {
							compute();
							synchronized (LOCK) {
								LOCK.notifyAll();
							}
						}
					}
				});
			}

		});
	}
	
	public static void compute() {
		int dx = P1.x - P3.x;
		int dy = P1.y - P3.y;
		JUMP = (long) (Math.sqrt(dx * dx + dy * dy) * 3.639);

		P1 = P3 = null;
		STATE = "JUMP";
	}

	public static void parse() {
		findActor(findTarget());
	}

	public static boolean findTarget() {
		if (IMAGE == null) {
			return false;
		}

		Color background = new Color(IMAGE.getRGB(2, 120), true);
		Color target = null;
		Point pl = null;
		Point pr = null;
		for (int h = 120; h < 360; h++) {
			if (target == null) {
				for (int w = 0; w < WIDTH; w++) {
					Color color = new Color(IMAGE.getRGB(w, h), true);
					if (!like(color, background, 12)) {
							target = color;
							pl = new Point(w, h);
							pr = new Point(w, h);
					}
				}
			} else {
				boolean all = true;
				for (int w = pl.x - 1; w < pr.x + 1; w++) {
					Color color = new Color(IMAGE.getRGB(w, h), true);
					if (like(color, target, 12)) {
						all = false;
						break;
					}
				}
				if (all) {
					continue;
				}

				pl.y = h;
				pr.y = h;

				boolean increase = false;
				
				for (int w = pl.x - 1; w > 0; w--) {
					Color color = new Color(IMAGE.getRGB(w, h), true);
					if (!like(color, target, 12)) {
						break;
					}
					pl.x = w;
					increase = true;
				}
				
				for (int w = pr.x + 1; w < WIDTH; w++) {
					Color color = new Color(IMAGE.getRGB(w, h), true);
					if (!like(color, target, 12)) {
						break;
					}
					pr.x = w;
					increase = true;
				}
				
				if (!increase) {
					break;
				}
			}
		}
		P3 = new Point();
		P3.x = (pl.x + pr.x) / 2;
		P3.y = (pl.y + pr.y) / 2;
		drawPoint(P3.x, P3.y);
		PANEL.repaint();
		
		return P3.x > WIDTH / 2;
	}

	public static boolean like(Color color1, Color color2, int d) {
		return (Math.abs(color1.getRed() - color2.getRed()) < d)
				&& (Math.abs(color1.getGreen() - color2.getGreen()) < d)
				&& (Math.abs(color1.getBlue() - color2.getBlue()) < d);
	}

	public static void findActor(boolean left) {
		Color actor = new Color(54, 60, 102);
		
		int min = left ? 0 : (WIDTH / 2);
		int max = left ? (WIDTH / 2) : WIDTH;
		
		for (int h = 607; h > 360; h--) {
			for (int w = min; w < max; w++) {
				Color color = new Color(IMAGE.getRGB(w, h), true);
				if (like(color, actor, 8)) {
					 P1 = new Point(w + 4, h - 2);
					break;
				}
			}
			if (P1 != null) {
				drawPoint(P1.x, P1.y);
				PANEL.repaint();
				break;
			}
		}
		
	}

	public static void drawPoint(int x, int y) {
		BufferedImage image = IMAGE;
		if (image != null) {
			if (x < 3) {
				x = 3;
			}
			if (x >= WIDTH) {
				x = WIDTH - 4;
			}
			if (y < 3) {
				y = 3;
			}
			if (y >= 720) {
				y = 720 - 4;
			}

			Graphics g = image.getGraphics();
			g.setColor(Color.RED);
			g.drawRect(x - 3, y - 3, 6, 6);
		}
	}

	public static BufferedImage scaleImage(BufferedImage originalImage, int width, int height) {
		BufferedImage newImage = new BufferedImage(width, height, originalImage.getType());
		Graphics g = newImage.getGraphics();
		g.drawImage(originalImage, 0, 0, width, height, null);
		g.dispose();
		return newImage;
	}

	public static void jump(long ms) {
		int x = random(300, 600);
		int y = random(300, 600);
		run("adb shell input swipe " + x + " " + y + " " + x + " " + y + " " + ms);
	}

	public static void run(String cmd) {
		System.out.println(cmd);
		try {
			Runtime.getRuntime().exec(cmd);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String screencap() {
		String name = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".png";
		File pathfile = new File("screen");
		if (!pathfile.exists()) {
			pathfile.mkdirs();
		}
		String path = pathfile.getAbsolutePath() + "\\";

		tsleep(1300 + JUMP * 2);
		run("cmd /c adb exec-out screencap -p > " + path + name);
		tsleep(1000);

		return path + name;
	}

	private static SecureRandom random = new SecureRandom();

	public static int random(int min, int max) {
		return random.nextInt(max) % (max - min + 1) + min;
	}

	public static void tsleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
