package main;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.objectweb.asm.ClassReader;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * 主要程序
 *
 * @author linchengnan
 */
public class HotSwapAgent {

	@Option(name = "-c", aliases = "--config", metaVar = "<file>", usage = "the config file to load the process config")
	private String config = "config.xml";

	@Option(name = "-l", aliases = "--logpath", metaVar = "<path>", usage = "the path for logs")
	private String logPath = "logs/";

	@Option(name = "-dt", aliases = "--daytime", forbids = {"-i", "--interval", "-f", "--file"},
		metaVar = "<yyyy-MM-dd HH:mm:ss>",
		usage = "[*]search the path for class files modified after specific daytime.")
	private String day_time = null;

	@Option(name = "-i", aliases = "--interval", forbids = {"-dt", "--daytime", "-f", "--file"},
		metaVar = "<seconds>",
		usage = "[*]search the path for class files modified within [<seconds>] seconds, the argument provided must be greater than 0.")
	private int interval = 0;

	@Option(name = "-f", aliases = "--file", forbids = {"-i", "--interval", "-dt", "--daytime"},
		metaVar = "<file>",
		usage = "[*]redefine the specific classes listed in the file, directory supported(recursively).")
	private String listedFile = null;

	@Argument(required = true, metaVar = "<process>", usage = "the process config to redefine.")
	private String nodeName = "server";

	public static void main(String[] args) {
		HotSwapAgent doctor = new HotSwapAgent();
		CmdLineParser parser = new CmdLineParser(doctor, ParserProperties.defaults().withUsageWidth(300));

		try {
			parser.parseArgument(args);
			doctor.checkArgs();
			LoggerHelper.init(HotSwapAgent.class, doctor.logPath, "main_" + LocalDate.now().toString() + ".log");
			doctor.processRedefine();
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.err.println("Usage: java " + HotSwapAgent.class.getName() + " [options] <process>");
			System.err.println("the program will redefine the classes for the config: <process>");
			System.err.println("[*]NOTE: options marked with '[*]' must be provided one and only one");
			parser.printUsage(System.err);
		}
	}

	private void checkArgs() throws IllegalArgumentException {
		if (day_time == null && listedFile == null && interval <= 0) {
			throw new IllegalArgumentException("[*] options must be provided!");
		}
	}

	private void processRedefine() throws Exception {
		Instant startInstant = Instant.now().minusSeconds(1);
		//获取配置
		Config config = getConfig(nodeName);
		if (config == null) {
			LoggerHelper.getLogger().severe("cannot find the config for node:" + nodeName);
			return;
		}
		LoggerHelper.getLogger().info("redefine config :\n" + config.toString());

		//获取用来入侵的jar包，即HotSwapAgent本身所在的jar包
		URL url = HotSwapAgent.class.getResource(HotSwapAgent.class.getSimpleName() + ".class");
		if (!"jar".equals(url.getProtocol())) {
			LoggerHelper.getLogger().log(Level.SEVERE, "HotSwapAgent need to be call in the jar file!");
			return;
		}
		String jarFileName = ((JarURLConnection) url.openConnection()).getJarFile().getName();
		LoggerHelper.getLogger().info("jarFile=" + jarFileName);

		Set<String> classFilePathList = new HashSet<>();

		//根据-dt --daytime的参数搜索
		if (day_time != null) {
			long leastModifiedTime = LocalDateTime.parse(day_time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			LoggerHelper.getLogger().info("searching path \"" + config.path + "\" for classes changed after daytime: \"" + day_time + "\".");
			classFilePathList.addAll(searchClass(new File(config.path), leastModifiedTime));
		}

		//根据-i --interval的参数搜索
		if (interval > 0) {
			LoggerHelper.getLogger().info("searching path \"" + config.path + "\" for the classes changed within " + interval + " seconds.");
			classFilePathList.addAll(searchClass(new File(config.path), System.currentTimeMillis() - interval * 1000));
		}

		//根据-f --file输入的文件里面列出的内容来获取要进行redefine的class
		if (listedFile != null) {
			LoggerHelper.getLogger().info("searching classes listed in file:" + listedFile);
			classFilePathList.addAll(loadListedClass());
		}

		if (classFilePathList.isEmpty()) {
			LoggerHelper.getLogger().warning("There're no class files need to be redefined!");
			return;
		}

		LoggerHelper.getLogger().info("searching done! " + classFilePathList.size() + " classes was found to be redefined.");

		LoggerHelper.getLogger().info("searching process using runtime cmd: " + config.cmd);
		Process process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", config.cmd});
		BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		ArrayList<String> processIdStrs = new ArrayList<>();
		while ((line = br.readLine()) != null) {
			LoggerHelper.getLogger().info("\t" + line);
			processIdStrs.add(line.trim());
		}
		br = new BufferedReader(new InputStreamReader(process.getErrorStream()));
		while ((line = br.readLine()) != null) {
			LoggerHelper.getLogger().warning(line);
		}
		int exitValue = process.waitFor();
		if (exitValue != 0) {
			LoggerHelper.getLogger().warning("\tsubprocess[" + config.cmd + "] exit with code: " + exitValue);
		}
		List<VirtualMachineDescriptor> vmList = VirtualMachine.list().stream().filter(vm -> processIdStrs.contains(vm.id())).collect(Collectors.toList());
		if (vmList.isEmpty()) {
			LoggerHelper.getLogger().warning("no vm was found to update!");
			return;
		}
		LoggerHelper.getLogger().info("searching done! " + vmList.size() + " vm found!");

		LoggerHelper.getLogger().info("extracting full class name from class file...");
		Map<String, String> classname_filepath_map = new TreeMap<>();
		for (String filePath : classFilePathList) {
			ClassReader classReader = new ClassReader(Files.readAllBytes(Paths.get(filePath)));
			String className = classReader.getClassName().replaceAll("/", ".");
			LoggerHelper.getLogger().info(String.format("\tpath=%s,className=%s", filePath, className));
			String previousFilePath = classname_filepath_map.putIfAbsent(className, filePath);
			if (previousFilePath != null) {
				throw new RuntimeException(String.format("Duplicate class[name='%s'] found! path1=[%s], path2=[%s], please check your config!", className, filePath, previousFilePath));
			}
		}
		String classInfo = classname_filepath_map.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining("|"));

		for (VirtualMachineDescriptor vm : vmList) {
			if (vm == null) {
				continue;
			}
			String logFileName = "agent" + vm.id() + "_" + LocalDate.now().toString() + ".log";
			try {
				LoggerHelper.getLogger().info("attaching vm: " + vm.toString());
				VirtualMachine jvm = VirtualMachine.attach(vm);
				String primeStr = logFileName + "|" + Paths.get(logPath).toAbsolutePath().toString() + "|" + classInfo;
				String argument = ZipUtil.gzip(primeStr);
				LoggerHelper.getLogger().info(String.format("%s chars in prime string, %s after zipped.", primeStr.length(), argument.length()));
				jvm.loadAgent(jarFileName, argument);
				jvm.detach();
				LoggerHelper.getLogger().info("attaching finished!");
			} catch (Exception e) {
				LoggerHelper.getLogger().log(Level.SEVERE, "HotSwapAgent-main", e);
			} finally {
				try {
					List<String> fileContens = Files.readAllLines(Paths.get(logPath).resolve(logFileName));
					if (fileContens.isEmpty()) {
						LoggerHelper.getLogger().log(Level.WARNING, "the agent exit with no log!");
					}
					LoggerHelper.getLogger().info("log from file '" + logFileName + "' after time " + startInstant.toString() + ":");
					boolean foundNewLog = false;
					for (String lineStr : fileContens) {
						if (!foundNewLog && lineStr.matches("^\\d+.*") && LoggerHelper.getTimeFromLog(lineStr) >= startInstant.toEpochMilli()) {
							foundNewLog = true;
						}
						if (foundNewLog) {
							LoggerHelper.getLogger().info("\t>" + lineStr);
						}
					}
				} catch (NoSuchFileException e) {
					LoggerHelper.getLogger().log(Level.WARNING, "the agent exit with no log!");
				} catch (Exception e) {
					LoggerHelper.getLogger().log(Level.WARNING, "read logfile error!", e);
				}
			}
		}
		LoggerHelper.getLogger().info("java doctor exit!");
		LoggerHelper.getLogger().info("");
	}


	private List<String> loadListedClass() throws IOException {
		List<String> result = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(listedFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				String trim = line.trim();
				if (trim.isEmpty() || trim.startsWith("#")) {
					continue;
				}
				result.addAll(loadClassFromFile(new File(trim)));
			}
		}
		return result;
	}

	private Collection<String> loadClassFromFile(File file) {
		Collection<String> result = new ArrayList<>();
		if (file.isDirectory()) {
			for (File subFile : Objects.requireNonNull(file.listFiles())) {
				result.addAll(loadClassFromFile(subFile));
			}
		} else {
			String classToLoad = file.toString();
			if (classToLoad.endsWith(".class")) {
				result.add(classToLoad);
				LoggerHelper.getLogger().info("\t" + classToLoad);
			}
		}
		return result;
	}

	private List<String> searchClass(File file, long leastModifiedTime) throws Exception {
		List<String> result = new ArrayList<>();
		if (file.isDirectory()) {
			for (File subFile : Objects.requireNonNull(file.listFiles())) {
				if (subFile.isDirectory()) {
					result.addAll(searchClass(subFile, leastModifiedTime));
				} else if (getChangeTime(subFile) >= leastModifiedTime
					           && subFile.getName().endsWith(".class")) {
					result.add(subFile.getAbsolutePath());
					LoggerHelper.getLogger().info("\t" + subFile.getAbsolutePath());
				}
			}
		}
		return result;
	}

	private long getChangeTime(File file) throws Exception {
		return ((FileTime) Files.getAttribute(file.toPath(), "unix:ctime")).toMillis();
	}

	private Config getConfig(String nodeName) throws Exception {
		SAXReader reader = new SAXReader();
		Document d = reader.read(new File(config));
		List<Node> list = d.selectNodes("config/process");
		for (Node n : list) {
			if (n.valueOf("@name").equalsIgnoreCase(nodeName)) {
				return new Config(n.valueOf("@name"), n.valueOf("@pid_command")
					, n.valueOf("@search_path"));
			}
		}
		return null;
	}
}
