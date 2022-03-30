package wonder.shaderdisplay.renderers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import fr.wonder.commons.loggers.AnsiLogger;
import fr.wonder.commons.loggers.Logger;
import wonder.shaderdisplay.Main;

public class ScriptRenderer extends FormatedInputRenderer {
	
	public static final Logger scriptLogger = new AnsiLogger("Script");
	
	@Override
	public void reloadInputFile() {
		String scriptOutput = runScriptFile(Main.options.scriptFile);
		if(scriptOutput != null)
			rebuildBuffersInput(scriptOutput);
	}
	
	private static String runScriptFile(File scriptFile) {
		if(!scriptFile.exists()) {
			Main.logger.err("The script file does not exist: " + scriptFile.getAbsolutePath());
			return null;
		}
		if(!scriptFile.isFile()) {
			Main.logger.err("Invalid script file: " + scriptFile.getAbsolutePath());
			return null;
		}
		try {
			String command = scriptFile.getAbsolutePath();
			long scriptTimestamp = System.currentTimeMillis();
			ProcessBuilder pb = new ProcessBuilder(command);
			ByteArrayOutputStream errbao = new ByteArrayOutputStream();
			ByteArrayOutputStream stdbao = new ByteArrayOutputStream();
			scriptLogger.debug("Running script: " + command);
			Process process = pb.start();
			redirectStreamInAnotherProcess(process.getInputStream(), stdbao);
			redirectStreamInAnotherProcess(process.getErrorStream(), errbao);
			int status = process.waitFor();
			scriptLogger.debug("Ran script in " + (System.currentTimeMillis() - scriptTimestamp) + "ms");
			String stderr = readProcessOutput(errbao);
			String stdout = readProcessOutput(stdbao);
			if(Main.options.scriptLogLength != 0) {
				String verboseOuput;
				if(stdout.length() > Main.options.scriptLogLength)
					verboseOuput = stdout.substring(0, Main.options.scriptLogLength) + "... (+" + (stdout.length()-Main.options.scriptLogLength) + " bytes)";
				else
					verboseOuput = stdout;
				scriptLogger.debug("Script output:\n" + verboseOuput);
			}
			if(!stderr.isBlank()) {
				scriptLogger.err("Script error:\n" + stderr);
				return null;
			}
			if(status != 0) {
				scriptLogger.err("The script exited with error: " + status);
				return null;
			}
			return stdout;
		} catch (IOException | InterruptedException e) {
			scriptLogger.err("Unexpected error while running script file: " + e.getMessage());
			return null;
		}
	}
	
	private static void redirectStreamInAnotherProcess(InputStream processStream, OutputStream redirection) {
		new Thread(() -> {
			try {
				processStream.transferTo(redirection);
			} catch (IOException e) {
				scriptLogger.err("An error occured in process stream redirection: " + e.getMessage());
			}
		}).start();
	}
	
	private static String readProcessOutput(ByteArrayOutputStream out) throws IOException {
		byte[] bytes = out.toByteArray();
		if(bytes.length == 0)
			return "";
		if(bytes[bytes.length-1] == '\n')
			return new String(bytes, 0, bytes.length-1);
		return new String(bytes);
	}
	
}
