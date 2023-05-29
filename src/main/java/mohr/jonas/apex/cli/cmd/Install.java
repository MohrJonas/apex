package mohr.jonas.apex.cli.cmd;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import lombok.val;
import mohr.jonas.apex.DistroboxAdapter;
import mohr.jonas.apex.ExportType;
import mohr.jonas.apex.cli.Spinner;
import mohr.jonas.apex.cli.Terminal;
import mohr.jonas.apex.data.Config;
import mohr.jonas.apex.data.DB;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class Install {

	private final Logger logger;
	@Inject
	private DistroboxAdapter adapter;
	@Inject
	private Config config;
	@Inject
	private Spinner spinner;
	@Inject
	private Terminal terminal;
	@Inject
	private DB db;

	@Inject
	public Install(Logger logger) {
		this.logger = logger;
	}

	public int call(@Nullable String containerName, boolean verbose, String packageName) {
		if (containerName != null) {
			val template = Config.getByName(config, containerName).orElseFatal();
			val beforeBinaries = adapter.getBinariesInContainer(containerName);
			spinner.spinUntilDone(String.format("Installing package '%s' in container '%s'", packageName, template.name()), CompletableFuture.supplyAsync(() -> {
				adapter.installPackageInContainer(containerName, template.install(), packageName);
				return null;
			}));
			db.addPackage(containerName, packageName);
			val afterBinaries = adapter.getBinariesInContainer(containerName);
			val deltaBinaries = ImmutableSet.copyOf(ArrayUtils.removeElements(afterBinaries, beforeBinaries));
			deltaBinaries.forEach((binary) -> askForExport(containerName, binary));
		} else {
			Arrays.stream(config.containers()).forEach((template) -> {
				terminal.success(">> Querying '%s'", template.name());
				spinner.spinUntilDone(String.format("Querying package '%s' in container '%s'", packageName, template.name()), CompletableFuture.supplyAsync(() -> {
					System.out.println(adapter.searchForPackageInContainer(template.name(), template.search(), packageName));
					return null;
				}));
			});
			val selection = terminal.askForNumber("Above are the available packages for query '%s' in all containers.\nSelect the index of the container you want to install from:", 1, config.containers().length);
			val template = config.containers()[selection - 1];
			val beforeBinaries = adapter.getBinariesInContainer(template.name());
			spinner.spinUntilDone(String.format("Installing package '%s' in container '%s'", packageName, template.name()), CompletableFuture.supplyAsync(() -> {
				adapter.installPackageInContainer(template.name(), template.install(), packageName);
				return null;
			}));
			db.addPackage(template.name(), packageName);
			val afterBinaries = adapter.getBinariesInContainer(template.name());
			val deltaBinaries = ArrayUtils.removeElements(afterBinaries, beforeBinaries);
			Arrays.stream(deltaBinaries).forEach((binary) -> {
				askForExport(template.name(), binary);
			});
		}
		return 0;
	}

	private void askForExport(String name, String binary) {
		if (terminal.askForBoolean(String.format(">> Export binary '%s'? (y/n)", binary), "y", "n"))
			adapter.exportFromContainer(name, ExportType.BINARY, binary);
	}
}
