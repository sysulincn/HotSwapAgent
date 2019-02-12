package main;


import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * 在目标JVM上入侵的程序
 *
 * @author linchengnan
 */
@SuppressWarnings("unused")
public class InjectedAgent {

	public static void agentmain(String args, Instrumentation inst) throws Exception {
		String[] argsArr = ZipUtil.gunzip(args).split("\\|");
		String logPath = argsArr[1];
		String logFileName = argsArr[0];
		LoggerHelper.init(InjectedAgent.class, logPath, logFileName);
		List<ClassDefinition> classDefinitionList = new ArrayList<>();
		for (int i = 2; i < argsArr.length; i++) {
			try {
				String[] arr = argsArr[i].split("=");
				String className = arr[0];
				String fileName = arr[1];
				byte[] bytes = Files.readAllBytes(Paths.get(arr[1]));
				classDefinitionList.add(new ClassDefinition(Class.forName(arr[0]), bytes));
			} catch (Throwable e) {
				LoggerHelper.getLogger().log(Level.SEVERE, "unable to load class :" + argsArr[i], e);
				throw e;
			}
		}
		try {
			LoggerHelper.getLogger().info("redefining " + classDefinitionList.size() + " classes...");
			inst.redefineClasses(classDefinitionList.toArray(new ClassDefinition[0]));
			LoggerHelper.getLogger().info("agent redefined success!");
		} catch (Throwable t) {
			LoggerHelper.getLogger().log(Level.SEVERE, "", t);
		} finally {
			LoggerHelper.getLogger().info("===================ends=================");
			LoggerHelper.closeLogger();
		}
	}
}
