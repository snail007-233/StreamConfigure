import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class FFserver {
	private String cfgPath;
	private Process ps = null;
	private boolean needExit = false;
	private Map<String, FFmpeg> ffmpegs;
	private Object syncSS = new Object(); // start stop sync object
	private Object syncAD = new Object(); // add delete sync object
	private static FFserver ffserver = null;

	private FFserver() {
		ffmpegs = new HashMap<String, FFmpeg>();
	}

	public synchronized static FFserver getFFserver() {
		if (ffserver == null)
			ffserver = new FFserver();
		return ffserver;
	}

	public void setPath(String path) {
		cfgPath = path;
	}

	boolean isExist(String name) {
		return ffmpegs.containsKey(name);
	}

	public void addFFmpeg(String from, String to) {
		synchronized (syncAD) {
			if (!from.startsWith("rtsp://")) {
				throw new IllegalArgumentException("Error rtsp url");
			}

			String str = from.substring(from.indexOf("rtsp://") + 7,
					from.lastIndexOf('/'));
			String identity = str.replace('.', '-').replace(':', '_');

			if (isExist(identity))
				return;

			FFmpeg ffmpeg = new FFmpeg(from, to);
			ffmpegs.put(identity, ffmpeg);
			ffmpeg.start();
		}
	}

	public void deleteFFmpeg(String from) {
		synchronized (syncAD) {
			if (!from.startsWith("rtsp://")) {
				throw new IllegalArgumentException("Error rtsp url");
			}

			String str = from.substring(from.indexOf("rtsp://") + 7,
					from.lastIndexOf('/'));
			String identity = str.replace('.', '-').replace(':', '_');

			if (!isExist(identity))
				return;

			FFmpeg ffmpeg = ffmpegs.get(identity);
			ffmpeg.stop();
			ffmpegs.remove(identity);
		}
	}

	void stopAllFFmpeg() {
		synchronized(syncAD) {
			Collection<FFmpeg> vals = ffmpegs.values();
			for(FFmpeg ffmpeg : vals) {
				ffmpeg.stop();
			}
		}
	}
	
	void startAllFFmpeg() {
		synchronized(syncAD) {
			Collection<FFmpeg> vals = ffmpegs.values();
			for(FFmpeg ffmpeg : vals) {
				ffmpeg.start();
			}
		}
	}
	
	/**
	 * 启动ffserver, 同时也会启动所有注册的ffmpeg
	 */
	public void start() {
		synchronized (syncSS) {
			if (ps != null) {
				ps.destroy();
				ps = null;
			}

			needExit = false;

			Thread t = new Thread(new Runnable() {
				String[] cmd = new String[] { "ffserver", "-f", cfgPath };

				@Override
				public void run() {
					try {
						while (needExit == false) {
							ps = Runtime.getRuntime().exec(cmd);
							BufferedReader br = new BufferedReader(
									new InputStreamReader(ps.getErrorStream()));
							String line;
							while ((line = br.readLine()) != null) {
								System.out.println(line);
							}

							ps.waitFor();
							Thread.sleep(1000);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			t.start();
		}
		
		startAllFFmpeg();
	}
	
	/**
	 * 停止ffserver,同时也会停止所有已经注册的ffmpeg
	 */
	public void stop() {
		synchronized (syncSS) {

			needExit = true;

			if (ps != null) {
				ps.destroy();
				ps = null;
			}
		}
		stopAllFFmpeg();
	}

	public static void main(String[] args) {
		FFserver ffserver = FFserver.getFFserver();
		ffserver.setPath(String
				.valueOf("/home/sijiewang/Projects/stream-media-test/ff.conf"));

		ffserver.start();

		ffserver.addFFmpeg(
				"rtsp://192.168.2.191:554/user=admin&password=admin&channel=1&stream=0.sdp",
				"http://localhost:8090/192-168-2-191_554.ffm");
		
		try {
			Thread.sleep(5 * 1000);
			ffserver.stopAllFFmpeg();
			Thread.sleep(5 * 1000);
			ffserver.startAllFFmpeg();
			
			Thread.sleep(60 * 1000);
			ffserver.deleteFFmpeg("rtsp://192.168.2.191:554/user=admin&password=admin&channel=1&stream=0.sdp");
			ffserver.stop();
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		}
		System.out.println("Main exit");
	}
}