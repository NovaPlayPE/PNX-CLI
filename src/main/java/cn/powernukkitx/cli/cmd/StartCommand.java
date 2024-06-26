package cn.powernukkitx.cli.cmd;

import cn.powernukkitx.cli.CLIConstant;
import cn.powernukkitx.cli.Main;
import cn.powernukkitx.cli.data.builder.JVMStartCommandBuilder;
import cn.powernukkitx.cli.data.locator.JarLocator;
import cn.powernukkitx.cli.data.locator.JavaLocator;
import cn.powernukkitx.cli.util.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;

import static cn.powernukkitx.cli.util.NullUtils.Ok;
import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "start", mixinStandardHelpOptions = true, resourceBundle = "cn.powernukkitx.cli.cmd.Start")
public final class StartCommand implements Callable<Integer> {
    private final ResourceBundle bundle = ResourceBundle.getBundle("cn.powernukkitx.cli.cmd.Start");

    @Option(names = {"-g", "--generate-only"}, descriptionKey = "generate-only", help = true)
    public boolean generateOnly;

    @Option(names = {"-r", "--restart"}, descriptionKey = "restart", help = true, negatable = true)
    public boolean restart;

    @Option(names = "--stdin", descriptionKey = "stdin", help = true)
    public String stdin;

    @Parameters(index = "0..*", hidden = true)
    public String[] args;

    private String[] startCommand = null;

    @Override
    public Integer call() {
        var cmdBuilder = new JVMStartCommandBuilder();
        var javaList = new JavaLocator("21", true).locate();
        if (javaList.isEmpty()) {
            Logger.error(ansi().fgBrightRed().a(new Formatter().format(bundle.getString("no-java21"), OSUtils.getProgramName())).fgDefault());
            return 1;
        }
        var java = javaList.get(0);
        cmdBuilder.setJvmExecutable(java.getFile());
        Logger.info(ansi().fgBrightYellow().a(new Formatter().format(bundle.getString("using-jvm"), java.getInfo().getVendor())).fgDefault());
        var pnxList = new JarLocator(CLIConstant.userDir, "cn.nukkit.PlayerHandle").locate();
        //auto install
        if (pnxList.isEmpty()) {
            File file = new File(CLIConstant.userDir, "PowerNukkitX-Core.zip");

            if (file.exists()) {
                new JarLocator(CLIConstant.userDir, "cn.nukkit.PlayerHandle").locate().forEach(each -> each.getFile().delete());
                try {
                    CompressUtils.uncompressZipFile(file, new File(""));
                    Files.deleteIfExists(file.toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                pnxList = new JarLocator(CLIConstant.userDir, "cn.nukkit.PlayerHandle").locate();
            } else {
                Logger.warn(ansi().fgBrightRed().a(new Formatter().format(bundle.getString("no-pnx"), OSUtils.getProgramName())).fgDefault());
                return 1;
            }
        }
        var libDir = new File(CLIConstant.userDir, "libs");
        if (!libDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            libDir.mkdirs();
        }
        var oldLibFiles = new LinkedList<>(Arrays.asList(Objects.requireNonNull(libDir.listFiles((dir, name) -> name.endsWith(".jar")))));
        if (oldLibFiles.size() < 32) {
            File file = new File(CLIConstant.userDir, "PowerNukkitX-Libs.zip");
            if (file.exists()) {
                File libs = new File(CLIConstant.userDir, "libs");
                FileUtils.deleteDir(libs);
                try {
                    CompressUtils.uncompressZipFile(file, libs);
                    Files.deleteIfExists(file.toPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                Logger.warn(ansi().fgBrightRed().a(new Formatter().format(bundle.getString("no-libs"), OSUtils.getProgramName())).fgDefault());
                return 1;
            }
        }
        var pnx = pnxList.get(0);
        cmdBuilder.addClassPath(pnx.getFile().getAbsolutePath());
        Logger.info(ansi().fgBrightYellow().a(new Formatter().format(bundle.getString("using-pnx"), Ok(pnx.getInfo().getGitInfo().orElse(null), info -> info.getMainVersion() + " - " + info.getCommitID(), "unknown"))).fgDefault());
        cmdBuilder.setStartTarget("cn.nukkit.Nukkit");
        cmdBuilder.addClassPath(new File(CLIConstant.userDir, "libs").getAbsolutePath() + File.separator + "*");
        cmdBuilder.addProperty("file.encoding", "UTF-8");
        cmdBuilder.addProperty("jansi.passthrough", "true");
        cmdBuilder.addProperty("terminal.ansi", "true");
        cmdBuilder.addAddOpen("java.base/java.lang");
        cmdBuilder.addAddOpen("java.base/java.io");
        cmdBuilder.addAddOpen("java.base/java.net");
        cmdBuilder.addXOption("mx", ConfigUtils.maxVMMemory());
        cmdBuilder.addXxOption("UseZGC", true);
        cmdBuilder.addXxOption("ZGenerational", true);
        cmdBuilder.addXxOption("UseStringDeduplication", true);
        for (var each : ConfigUtils.vmParams()) {
            cmdBuilder.addOtherArgs(each);
        }
        for (var each : ConfigUtils.addOpens()) {
            cmdBuilder.addAddOpen(each);
        }
        for (var each : ConfigUtils.xOptions()) {
            cmdBuilder.addXOption(each);
        }
        for (var each : ConfigUtils.xxOptions()) {
            cmdBuilder.addXxOption(each);
        }
        if (generateOnly) {
            Logger.raw(cmdBuilder.build() + "\n");
            return 0;
        }
        cmdBuilder.addProperty("pnx.cli.path", OSUtils.getProgramPath());
        cmdBuilder.addProperty("pnx.cli.version", CLIConstant.version);
        startCommand = cmdBuilder.build().split(" ");
        if (restart) {
            var result = start();
            while (true) {
                if (!InputUtils.pressEnterToStopWithTimeLimit(10000)) {
                    result = start();
                } else {
                    return result;
                }
            }
        } else {
            return start();
        }
    }

    enum GraalStatus {
        NotFound,
        Standard,
        Oracle,
        LowVersion
    }

    private GraalStatus getGraalStatus(JavaLocator.JavaInfo javaInfo) {
        var vendor = javaInfo.getVendor().toLowerCase();
        if (!vendor.contains("graal")) {
            return GraalStatus.NotFound;
        }
        if (vendor.contains("oracle graalvm")) {
            return GraalStatus.Oracle;
        }
        var index = vendor.indexOf("2", vendor.indexOf("graalvm"));
        if (index == -1) {
            return GraalStatus.NotFound;
        }
        var version = vendor.substring(index, index + 4);
        return Integer.parseInt(version.replace(".", "")) < 222 ? GraalStatus.LowVersion : GraalStatus.Standard;
    }

    private int start() {
        System.gc();
        try {
            var useStdinFile = stdin != null && !"".equals(stdin.trim());
            var builder = new ProcessBuilder().command(startCommand);
            if (useStdinFile) {
                builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectError(ProcessBuilder.Redirect.INHERIT);
            } else {
                builder.inheritIO();
            }
            var process = builder.start();
            Main.pnxRunning = true;
            if (useStdinFile) {
                var stdinFile = new File(CLIConstant.userDir, stdin);
                if (stdinFile.exists() && stdinFile.isFile() && stdinFile.canRead() && stdinFile.canWrite()) {
                    Main.getTimer().scheduleAtFixedRate(new TimerTask() {
                        long lastUpdateTime = -1;

                        @Override
                        public void run() {
                            try {
                                if (!process.isAlive()) {
                                    this.cancel();
                                }
                                if (stdinFile.lastModified() > lastUpdateTime) {
                                    var tmp = Files.readAllBytes(stdinFile.toPath());
                                    process.getOutputStream().write(tmp);
                                    process.getOutputStream().flush();
                                    try (var fileWriter = new FileWriter(stdinFile)) {
                                        fileWriter.write("");// 清空
                                        fileWriter.flush();
                                    }
                                    lastUpdateTime = stdinFile.lastModified();
                                }
                            } catch (Exception ignore) {

                            }
                        }
                    }, 1000, 1000);
                }
            }
            int exitValue = process.waitFor();
            Main.pnxRunning = false;
            return exitValue;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Main.pnxRunning = false;
            return 1;
        }
    }
}
