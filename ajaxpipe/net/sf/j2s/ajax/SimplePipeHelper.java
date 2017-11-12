/*******************************************************************************
 * Copyright (c) 2007 java2script.org and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Zhou Renjian - initial API and implementation
 *******************************************************************************/
package net.sf.j2s.ajax;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.sf.j2s.ajax.SimpleSerializable;
import net.sf.j2s.annotation.J2SIgnore;
import net.sf.j2s.annotation.J2SNative;

/**
 * 
 * @author zhou renjian
 */
public class SimplePipeHelper {
	
	public static interface IPipeThrough {
		public void helpThrough(SimplePipeRunnable pipe, SimpleSerializable[] objs);
	}
	
	public static interface IPipeClosing {
		public void helpClosing(SimplePipeRunnable pipe);
	}
	
	@J2SIgnore
	public static int MAX_ITEMS_PER_QUERY = 100;
	
//	@J2SIgnore
//	private static Map<String, List<SimpleSerializable>> pipeMap = null;
	
	@J2SIgnore
	private static boolean monitored = false;
	
	@J2SIgnore
	private static long monitoringInterval = 10000; // 10s
	
	// allPipes is for JavaScript, as pipes is for Java
	static Object allPipes = null;
	
	@J2SIgnore
	private static Map<String, SimplePipeRunnable> pipes = new ConcurrentHashMap<String, SimplePipeRunnable>(50);

	@J2SIgnore
	private static BlockingQueue<SimplePipeRunnable> toBeDestroyedPipes = new LinkedBlockingQueue<SimplePipeRunnable>();
	
	@J2SIgnore
	private SimplePipeHelper() {
		//
	}

	/*
	 * Browser mode
	 */
	@J2SNative({
		"if (key == null || pipe == null) return;",
		"var sph = net.sf.j2s.ajax.SimplePipeHelper;",
		"if (sph.allPipes == null) {",
		"	sph.allPipes = new Object ();",
		"}",
		"sph.allPipes[key] = pipe;"
	})
	public static void registerPipe(String key, SimplePipeRunnable pipe) {
		if (key == null || pipe == null) return;
		pipes.put(key, pipe);
	}
	
	/*
	 * Server side
	 */
	@J2SIgnore
	synchronized static String registerPipe(SimplePipeRunnable pipe) {
		if (pipe.pipeKey != null) {
			System.out.println("ERROR!!! pipeKey should be null here! " + pipe.pipeKey);
		}
		// if (pipe == null) return null; // should never register null pipe!
		String key = nextPipeKey();
		while (pipes.get(key) != null) {
			key = nextPipeKey();
		}
		pipes.put(key, pipe); // FIXME: In rare case, it will override another pipe
		
//		if (pipeMap == null) {
//			pipeMap = new ConcurrentHashMap<String, List<SimpleSerializable>>();
//		}
//		List<SimpleSerializable> list = pipeMap.get(key);
//		if (list == null) {
//			list = new LinkedList<SimpleSerializable>();
//			pipeMap.put(key, list);
//		}
		
		if (pipe.pipeData == null) {
			pipe.pipeData = new LinkedList<SimpleSerializable>();
		}
		return key;
	}

	/*
	 * Generate random pipe key.
	 */
	@J2SIgnore
	static String nextPipeKey() {
		StringBuilder builder = new StringBuilder(SimplePipeRequest.PIPE_KEY_LENGTH);
		for (int i = 0; i < SimplePipeRequest.PIPE_KEY_LENGTH; i++) {
			int r = (int) Math.floor(Math.random() * 62); // 0..61, total 62 numbers
			if (r < 10) {
				builder.append((char) (r + '0'));
			} else if (r < 10 + 26) {
				builder.append((char) ((r - 10) + 'a'));
			} else {
				builder.append((char) ((r - 10 - 26) + 'A'));
			}
		}
		return builder.toString();
	}
	
	@J2SNative({
		"var sph = net.sf.j2s.ajax.SimplePipeHelper;",
		"if (sph.allPipes != null) {",
		"	delete sph.allPipes[key];",
		"}"
	})
	public static void removePipe(String key) {
		if (key == null) {
			System.out.println("Removing pipe for null key???");
			new RuntimeException("Removing null pipe key").printStackTrace();
			return;
		}
		SimplePipeRunnable pipe = pipes.remove(key);
		if (pipe != null) {
			pipe.pipeAlive = false;
			pipe.pipeClearData();
			synchronized (pipe) {
				pipe.notifyAll();
			}
		}
//		if (pipeMap != null) {
//			if (pipeMap.remove(key) != null && pipe != null) {
//				synchronized (pipe) {
//					pipe.notifyAll();
//				}
//			}
//		}
	}

	@J2SNative({
		"var ps = net.sf.j2s.ajax.SimplePipeHelper.allPipes;",
		"if (ps == null || key == null) return null;",
		"return ps[key];"
	})
	public static SimplePipeRunnable getPipe(String key) {
		if (key == null) return null;
		return pipes.get(key);
	}

	// Use this method to avoid HTTP repeat attacks
	@J2SIgnore
	public static SimplePipeRunnable checkPipeWithHash(String key, long hash) {
		SimplePipeRunnable p = getPipe(key);
		if (p == null) {
			return null;
		}
		if (p.lastHash >= hash) {
			return null;
		}
		p.lastHash = hash;
		return p;
	}
	
//	@J2SIgnore
//	public static List<SimpleSerializable> getPipeDataList(String key) {
//		if (pipeMap == null) {
//			return null;
//		}
//		return pipeMap.get(key);
//	}

	@J2SIgnore
	public static void pipeIn(String key, SimpleSerializable[] ss) {
		SimplePipeRunnable pipe = getPipe(key);
		List<SimpleSerializable> list = pipe != null ? pipe.pipeData : null; //getPipeDataList(key);
		if (pipe == null || list == null) {
			System.out.println("There are no pipe listening?!!!!");
			return; // throw exception?
		}
		int v = pipe.getSimpleVersion();
		synchronized (list) {
			boolean hasNewPriority = false;
			for (int i = 0; i < ss.length; i++) {
				SimpleSerializable s = ss[i];
				if (v >= 202) {
					s.setSimpleVersion(v);
				}
				if (s instanceof ISimpleCacheable) {
					ISimpleCacheable c = (ISimpleCacheable) s;
					if (c.isCached()) {
						// in buffer and not sent yet
						continue;
					} else {
						int idx = list.indexOf(s);
						if (idx != -1 && idx >= pipe.getLastBufferedIndex()) {
							// same object but with updated properties maybe!
							SimpleSerializable existed = list.get(idx);
							if (existed instanceof ISimpleCacheable) {
								ISimpleCacheable ec = (ISimpleCacheable) existed;
								ec.synchronizeFrom(c);
							}
							continue;
						}
					}
					c.setCached(true);
				}
				list.add(s);
				if (!hasNewPriority && s instanceof ISimplePipePriority) {
					hasNewPriority = true;
				}
			}
			if (hasNewPriority && ss.length > MAX_ITEMS_PER_QUERY) { // 100 by default
				// sort list!
				Collections.sort(list, new Comparator<SimpleSerializable>() {
					
					public int compare(SimpleSerializable o1, SimpleSerializable o2) {
						boolean v1 = o1 instanceof ISimplePipePriority;
						boolean v2 = o2 instanceof ISimplePipePriority;
						if (v1 && v2) {
							ISimplePipePriority sp1 = (ISimplePipePriority) o1;
							ISimplePipePriority sp2 = (ISimplePipePriority) o2;
							return sp2.getPriority() - sp1.getPriority();
						} else if (v1) {
							ISimplePipePriority sp1 = (ISimplePipePriority) o1;
							return ISimplePipePriority.IMPORTANT - sp1.getPriority();
						} else if (v2) {
							ISimplePipePriority sp2 = (ISimplePipePriority) o2;
							return sp2.getPriority() - ISimplePipePriority.IMPORTANT;
						}
						return 0;
					}
					
				});
			}
		}
		synchronized (pipe) {
			// Notify pipe in!
			pipe.notify();
		}
	}

	@J2SIgnore
	public static boolean isPipeLive(String key) {
		SimplePipeRunnable pipe = getPipe(key);
		if (pipe != null) {
			return pipe.isPipeLive();
		}
		return false;
	}
	
	@J2SIgnore
	public static boolean notifyPipeStatus(String key, boolean live) {
		SimplePipeRunnable pipe = getPipe(key);
		if (pipe != null && pipe.isPipeLive()) {
			pipe.updateStatus(live);
			return true;
		}
		return false;
	}

	@J2SIgnore
	static void helpClosing(SimplePipeRunnable pipe) {
		if (pipe.closer != null) {
			pipe.closer.helpClosing(pipe);
		} else {
			pipe.pipeClosed();
		}
	}
	
	@J2SIgnore
	public static String printStatistics2() {
		StringBuilder builder = new StringBuilder();
		builder.append("Pipe monitor<br />\r\n");
		builder.append("Totoal pipe count: " + pipes.size() + "<br />\r\n");
//		buffer.append("Total pipe map count: " + pipeMap.size() + "<br />\r\n");
		int i = 0;
		for (Iterator<SimplePipeRunnable> itr = pipes.values().iterator(); itr.hasNext();) {
			SimplePipeRunnable p = (SimplePipeRunnable) itr.next();
			i++;
			List<SimpleSerializable> list = p.pipeData; //pipeMap.get(key);
			if (p instanceof CompoundPipeRunnable) {
				CompoundPipeRunnable cp = (CompoundPipeRunnable) p;
				int activeCount = 0;
				for (int j = 0; j < cp.pipes.length; j++) {
					if (cp.pipes[j] != null) {
						activeCount++;
					}
				}
				if (activeCount > 2) {
					builder.append(i + " Pipe (active=" + activeCount + ") " + cp.pipeKey + " status=" + cp.status + " pipeAlive=" + cp.isPipeLive() + " created=" + new Date(cp.lastSetup) + "<br />\r\n");
				}
			}
			if (list != null) {
				int size = list.size();
				if (size > 20) {
					builder.append(i + "::: pipe " + p.pipeKey + " size : " + size + " / " + p.pipeAlive + "<br />\r\n");
				}
			}
		}
		return builder.toString();
	}

	@J2SIgnore
	public static String printStatistics() {
		StringBuilder builder = new StringBuilder();
		builder.append("Pipe monitor<br />\r\n");
		builder.append("Totoal pipe count: " + pipes.size() + "<br />\r\n");
//		buffer.append("Total pipe map count: " + pipeMap.size() + "<br />\r\n");
		int i = 0;
		for (Iterator<SimplePipeRunnable> itr = pipes.values().iterator(); itr.hasNext();) {
			SimplePipeRunnable p = (SimplePipeRunnable) itr.next();
			i++;
			List<SimpleSerializable> list = p.pipeData; //pipeMap.get(key);
			if (p instanceof CompoundPipeRunnable) {
				CompoundPipeRunnable cp = (CompoundPipeRunnable) p;
				builder.append(i + "Pipe " + cp.pipeKey + " status=" + cp.status + " pipeAlive=" + cp.isPipeLive() + " created=" + new Date(cp.lastSetup) + "<br />\r\n");
				for (int j = 0; j < cp.pipes.length; j++) {
					CompoundPipeSession ps = cp.pipes[j];
					if (ps != null) {
						builder.append(j + " : " + ps.session + " / " + ps.isPipeLive() + " pipeAlive=" + ps.pipeAlive + "<br />\r\n");
					}
				}
			}
			if (list != null) {
				int size = list.size();
				builder.append("::: pipe " + p.pipeKey + " size : " + size + " / " + p.pipeAlive + "<br />\r\n");
			}
		}
		return builder.toString();
	}

	@J2SIgnore
	public static long getMonitoringInterval() {
		return monitoringInterval;
	}

	@J2SIgnore
	public static void setMonitoringInterval(long monitoringInterval) {
		SimplePipeHelper.monitoringInterval = monitoringInterval;
	}

	@J2SIgnore
	private static void monitoringAllPipes() {
		while (true) {
			try {
				Thread.sleep(monitoringInterval);
			} catch (InterruptedException e) {
			}
			Object[] allPipes = pipes.values().toArray();
			for (int i = 0; i < allPipes.length; i++) {
				final SimplePipeRunnable pipe = (SimplePipeRunnable) allPipes[i];
				if (!pipe.pipeManaged) {
					continue;
				}
				long now = System.currentTimeMillis();
				try {
					if (!pipe.isPipeLive() || (pipe instanceof CompoundPipeRunnable
							&& ((CompoundPipeRunnable) pipe).isEmpty()
							&& now - ((CompoundPipeRunnable) pipe).lastSetup > 30000)) {
						//System.out.println("Pipe " + pipe.pipeKey + " live status is " + pipe.isPipeLive());
						if (now - pipe.lastLiveDetected > pipe.pipeWaitClosingInterval()) {
							String pipeKey = pipe.pipeKey;
							asyncDestroyPipe(pipe);
							if (pipeKey != null && pipeKey.length() > 0) {
								removePipe(pipeKey);
							}
						}
					} else {
						if (pipe instanceof CompoundPipeRunnable) {
							CompoundPipeRunnable cp = (CompoundPipeRunnable) pipe;
							for (int j = 0; j < cp.pipes.length; j++) {
								CompoundPipeSession ps = cp.pipes[j];
								if (ps == null) {
									continue;
								}
								if (ps.isPipeLive()) {
									ps.lastLiveDetected = now;
								} else if (now - ps.lastLiveDetected > SimplePipeRequest.pipeLiveNotifyInterval * 3 + monitoringInterval + ps.pipeWaitClosingInterval()) {
									asyncDestroyPipe(ps);
								}
							}
						}
						pipe.lastLiveDetected = now;
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@J2SIgnore
	private static void killingPipes() {
		while (true) {
			SimplePipeRunnable pipe = null;
			try {
				pipe = toBeDestroyedPipes.poll(30, TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			if (pipe != null) {
				//System.out.println("Killing pipe " + pipe + " :// " + System.currentTimeMillis());
				try {
					if (pipe.closer != null) {
						pipe.closer.helpClosing(pipe); // will call pipe.pipeDestroy()
					} else {
						pipe.pipeClosed(); // will call pipe.pipeDestroy()
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
				//System.out.println("Killed pipe " + pipe + " :// " + System.currentTimeMillis());
			} else {
				//System.out.println("?? Empty loop?");
			}
		}
	}
	
	@J2SIgnore
	static void asyncDestroyPipe(final SimplePipeRunnable pipe) {
		//System.out.println("To destroy pipe " + pipe);
		if (!toBeDestroyedPipes.contains(pipe)) {
			toBeDestroyedPipes.offer(pipe);
		}
		/*
		ThreadUtils.runTask(new Runnable() {
			@Override
			public void run() {
				try {
					if (pipe.closer != null) {
						pipe.closer.helpClosing(pipe); // will call pipe.pipeDestroy()
					} else {
						pipe.pipeClosed(); // will call pipe.pipeDestroy()
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}, "Destroy Pipe Thread", false);
		// */
	}
	
	@J2SIgnore
	static void monitoringPipe(SimplePipeRunnable pipe) {
		long now = System.currentTimeMillis();
		pipe.lastLiveDetected = now;
		if (monitored) {
			return;
		}
		monitored = true;
		
		Thread monitorThread = new Thread(new Runnable() {
			public void run() {
				monitoringAllPipes();
			}
		}, "Simple Pipe Managed Session Monitor");
		monitorThread.setDaemon(true);
		monitorThread.start();
		
		Thread killThread = new Thread(new Runnable() {
			public void run() {
				killingPipes();
			}
		}, "Simple Pipe Managed Session Killer");
		killThread.setDaemon(true);
		killThread.start();
	}
	
}
