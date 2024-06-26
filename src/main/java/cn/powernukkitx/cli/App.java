package cn.powernukkitx.cli;

import cn.powernukkitx.cli.cmd.StartCommand;
import cn.powernukkitx.cli.cmd.SysInstallCommand;
import cn.powernukkitx.cli.util.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.Formatter;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;

import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "pnx", aliases = {"pnx", "PNX", "cli"}, version = CLIConstant.version, mixinStandardHelpOptions = true,
        resourceBundle = "cn.powernukkitx.cli.App", subcommands = {
        SysInstallCommand.class,
        StartCommand.class
})
public final class App implements Callable<Integer> {

    @SuppressWarnings("unused")
    @Option(names = {"-l", "--lang", "--language"}, paramLabel = "<lang>", descriptionKey = "lang")
    private String ignoredLocale;

    @Option(names = "--config-path", paramLabel = "<config-path>", descriptionKey = "config-path")
    public String configFilePath;

    @SuppressWarnings("unused")
    @Option(names = {"-u", "--update"}, descriptionKey = "update", negatable = true, defaultValue = "true")
    public boolean ignoredCheckUpdate;

    @Parameters(index = "0..*", hidden = true)
    public String[] args;

    private final ResourceBundle bundle = ResourceBundle.getBundle("cn.powernukkitx.cli.App");

    @Override
    public Integer call() {
        if (StringUtils.notEmpty(configFilePath)) {
            var file = new File(configFilePath);
            if (file.exists() && file.canRead() && file.canWrite()) {
                ConfigUtils.globalConfigFile = file;
                ConfigUtils.parseConfigFile(file);
            } else {
                Logger.error(ansi().fgBrightRed().a(new Formatter().format(bundle.getString("invalid-file"), configFilePath)).fgDefault());
            }
        }
        var start = new StartCommand();
        start.args = args;
        if (args != null && args.length != 0) {
            Logger.error(ansi().fgBrightYellow().a(new Formatter().format(bundle.getString("args"), OSUtils.getProgramName())).fgDefault());
            CommandLine.usage(this, System.out);
            return 1;
        }
        start.generateOnly = false;
        var ret = start.call();
        if (ret != 0) {
            InputUtils.pressEnterToContinue();
        }
        return ret;
    }
}
