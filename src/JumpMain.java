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

	// 绘制在界面上的图像
	private static BufferedImage IMAGE = null;

	// 绘制图像的panel
	private static JPanel PANEL = null;

	// 界面上绘制的图像的宽。高是固定值，宽用比例计算出来
	private static int WIDTH = 0;
	// 界面上绘制的图像的高，固定值。
	private static final int HEIGHT = 720;

	// 角色位置
	private static Point P1 = null;
	// 目的位置
	private static Point P3 = null;

	// 状态，CLICK为可点击，JUMP为正在跳跃
	private static String STATE = "CLICK";
	// 跳跃时长（毫秒）
	private static long JUMP = 0;

	// 状态锁
	private static final Object LOCK = new Object();
	// 运行标识
	private static volatile boolean RUNNING = true;

	public static void main(String[] args) {
		// 跳一跳线程
		new Thread() {
			@Override
			public void run() {
				while (RUNNING) {
					// 如果时间已确定，则立即跳跃
					if (JUMP > 0) {
						jump(JUMP);
					}

					// 截图
					String filepath = screencap();
					try {
						// 读取截图
						File file = new File(filepath);
						IMAGE = ImageIO.read(file);
						// 删除截图
						file.delete();
						
						// 截图读取成功，则缩放图像，计算宽。
						if (IMAGE != null) {
							WIDTH = IMAGE.getWidth() * HEIGHT / IMAGE.getHeight();
							IMAGE = scaleImage(IMAGE, WIDTH, HEIGHT);
						}
					} catch (IOException e) {
						IMAGE = null;
					}

					// 截图失败，则稍后重新截取
					if (IMAGE == null) {
						tsleep(1000);
						continue;
					}

					// 检查运行标识
					if (!RUNNING) {
						break;
					}

					// 绘制图像
					PANEL.repaint();

					// 分析图像，检测角色位置和目的位置
					parse();

					// 检测成功，则计算跳跃时长。并进行下一轮循环执行跳跃
					if (P1 != null && P3 != null) {
						compute();
						continue;
					}

					// 检测失败，则状态设置为可点击
					STATE = "CLICK";

					// 等待点击完成
					synchronized (LOCK) {
						try {
							LOCK.wait();
						} catch (InterruptedException e) {
						}
					}
				}
			}
		}.start();

		// 界面
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
						// 窗口关闭后，停止跳一跳线程
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
						// 绘制图像
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
						// 非CLICK状态，忽略点击事件
						if (!"CLICK".equals(STATE)) {
							return;
						}

						if (e.getButton() == 1) {
							// 左键为角色位置
							P1 = new Point();
							P1.x = e.getPoint().x;
							P1.y = e.getPoint().y;

							// 在角色位置做标记
							drawPoint(P1.x, P1.y);
							// 重新绘制
							PANEL.repaint();
						} else if (e.getButton() == 3) {
							// 左键为目标位置
							P3 = new Point();
							P3.x = e.getPoint().x;
							P3.y = e.getPoint().y;

							// 在目标位置做标记
							drawPoint(P3.x, P3.y);
							// 重新绘制
							PANEL.repaint();
						}

						// 角色位置和目标位置都确定后，计算跳跃时长
						if (P1 != null && P3 != null) {
							compute();
							// 唤醒跳一跳线程
							synchronized (LOCK) {
								LOCK.notifyAll();
							}
						}
					}
				});
			}

		});
	}
	
	// 计算跳跃时长
	public static void compute() {
		int dx = P1.x - P3.x;
		int dy = P1.y - P3.y;
		// 两点间的距离乘以系数
		JUMP = (long) (Math.sqrt(dx * dx + dy * dy) * 3.639);

		// 清空位置，设置状态为跳跃
		P1 = P3 = null;
		STATE = "JUMP";
	}

	// 分析图像，检测角色位置和目标位置
	public static void parse() {
		findActor(findTarget());
	}

	// 检测目标位置
	public static boolean findTarget() {
		if (IMAGE == null) {
			return false;
		}

		// 取背景色
		Color background = new Color(IMAGE.getRGB(2, 120), true);
		// 目标颜色
		Color target = null;
		Point pl = null;
		Point pr = null;
		// 在上半图像逐行扫描
		for (int h = 120; h < 360; h++) {
			if (target == null) {
				// 目标颜色未确定时，一旦遇到非背景色，则认为遇到目标
				for (int w = 0; w < WIDTH; w++) {
					Color color = new Color(IMAGE.getRGB(w, h), true);
					if (!like(color, background, 12)) {
						// 记录背景色和位置
						target = color;
						pl = new Point(w, h);
						pr = new Point(w, h);
					}
				}
			} else {
				// 目标颜色已确定
				
				// 如果一行都跟背景色不一致，则跳过该行
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

				// 从中心点往两端检测，如果遇到目标变更大，则继续。如果目标变更小，则说明到达目标最宽处。中心点就在这里
				pl.y = h;
				pr.y = h;

				boolean increase = false;
				
				// 检测左端
				for (int w = pl.x - 1; w > 0; w--) {
					Color color = new Color(IMAGE.getRGB(w, h), true);
					if (!like(color, target, 12)) {
						break;
					}
					pl.x = w;
					increase = true;
				}
				// 检测右端
				for (int w = pr.x + 1; w < WIDTH; w++) {
					Color color = new Color(IMAGE.getRGB(w, h), true);
					if (!like(color, target, 12)) {
						break;
					}
					pr.x = w;
					increase = true;
				}
				
				// 宽度没有增加，已经最宽了，说明到达中心点所在行
				if (!increase) {
					break;
				}
			}
		}
		// 记录目标位置中心点
		P3 = new Point();
		P3.x = (pl.x + pr.x) / 2;
		P3.y = (pl.y + pr.y) / 2;
		// 在目标位置绘制标记
		drawPoint(P3.x, P3.y);
		// 重绘图像
		PANEL.repaint();
		
		// 返回目标位置是不是在右边
		return P3.x > WIDTH / 2;
	}

	// 颜色是否接近
	public static boolean like(Color color1, Color color2, int d) {
		return (Math.abs(color1.getRed() - color2.getRed()) < d)
				&& (Math.abs(color1.getGreen() - color2.getGreen()) < d)
				&& (Math.abs(color1.getBlue() - color2.getBlue()) < d);
	}

	// 检测角色位置。参数：角色是否在左边
	public static void findActor(boolean left) {
		// 角色的颜色
		Color actor = new Color(54, 60, 102);
		
		// 检测到边界
		int min = left ? 0 : (WIDTH / 2);
		int max = left ? (WIDTH / 2) : WIDTH;
		
		// 根据颜色，查找角色的位置
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

	// 绘制一个标记
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

	// 等比例缩放图像
	public static BufferedImage scaleImage(BufferedImage originalImage, int width, int height) {
		BufferedImage newImage = new BufferedImage(width, height, originalImage.getType());
		Graphics g = newImage.getGraphics();
		g.drawImage(originalImage, 0, 0, width, height, null);
		g.dispose();
		return newImage;
	}

	// 执行跳一跳。屏幕触摸点随机生成
	public static void jump(long ms) {
		int x = random(300, 600);
		int y = random(300, 600);
		run("adb shell input swipe " + x + " " + y + " " + x + " " + y + " " + ms);
	}

	// 执行命令
	public static void run(String cmd) {
		//System.out.println(cmd);
		try {
			Runtime.getRuntime().exec(cmd);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// 截图
	public static String screencap() {
		String name = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".png";
		File pathfile = new File("screen");
		if (!pathfile.exists()) {
			pathfile.mkdirs();
		}
		String path = pathfile.getAbsolutePath() + "\\";

		// 截图前等一会，等待跳跃完成。跳跃时间越长等待越长
		tsleep(1300 + JUMP * 2);
		run("cmd /c adb exec-out screencap -p > " + path + name);
		// 截图命令发出后等一会，等待截图保存成功
		tsleep(1000);

		// 返回截图文件地址
		return path + name;
	}

	// 
	private static SecureRandom random = new SecureRandom();

	// 生成随机数
	public static int random(int min, int max) {
		return random.nextInt(max) % (max - min + 1) + min;
	}

	// 线程睡眠
	public static void tsleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
