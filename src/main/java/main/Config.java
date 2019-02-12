package main;

public class Config {
	final String cmd;
	final String path;
	private final String name;

	Config(String name, String cmd, String path) {
		super();
		this.name = name;
		this.cmd = cmd;
		this.path = path;
	}

	@Override
	public String toString() {
		return "Config{" +
			       "name='" + name + '\'' +
			       ", cmd='" + cmd + '\'' +
			       ", path='" + path + '\'' +
			       '}';
	}
}
