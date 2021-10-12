package util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class AutoPrinter {

  private static final Logger logger;
  private static final String PRINTER_NAME;
  private static final String WATCH_DIR;
  private static final String FILE_NAME_PATTERN;

  static {
    logger = LogManager.getLogger(AutoPrinter.class.getName());

    Properties props = new Properties();
    try (InputStream in = Files.exists(Paths.get("./config.properties"))
      ? new FileInputStream("./config.properties")
      : AutoPrinter.class.getResourceAsStream("/config.properties")) {
      props.load(in);
    } catch (IOException | NullPointerException ex) {
      logger.error("Application configuration not found!\n", ex);
      throw new AssertionError("Application configuration not found", ex);
    }

    PRINTER_NAME = props.getProperty("printerName");
    WATCH_DIR = props.getProperty("watchDir");
    FILE_NAME_PATTERN = props.getProperty("fileNamePattern");
  }

  private static AutoPrinter instance;
  private final Path dir;
  private final WatchService watcher;
  private final Pattern fileNamePattern;
  private final List<Path> trash;

  private AutoPrinter() throws IOException {
    watcher = FileSystems.getDefault().newWatchService();

    dir = Paths.get(WATCH_DIR);
    dir.register(watcher, ENTRY_CREATE);

    fileNamePattern = Pattern.compile(FILE_NAME_PATTERN);

    trash = new LinkedList<>();
  }

  private void processEvents() {
    while (true) {
      WatchKey crntWatchKey;
      try {
        crntWatchKey = watcher.take();
      } catch (InterruptedException ex) {
        logger.error("Watcher interrupted!\n", ex);
        break;
      } catch (ClosedWatchServiceException ex) {
        logger.info("Watcher is closed!\n");
        break;
      }

      while (!trash.isEmpty()) {
        if (trash.get(0).toFile().exists()) {
          trash.get(0).toFile().delete();
        }
        trash.remove(0);
      }

      for (WatchEvent<?> event : crntWatchKey.pollEvents()) {
        if (event.kind() == OVERFLOW) {
          continue;
        }

        Path path = dir.resolve(((WatchEvent<Path>) event).context());

        logger.info("{}, {}\n", event.kind().name(), path);

        if (fileNamePattern.matcher(path.toFile().getName()).matches()) {
          print(path);
        }
      }

      boolean valid = crntWatchKey.reset();
      if (!valid) {
        logger.error("WatchKey reset failed!\n");
        break;
      }
    }
  }

  private void print(Path path) {
    PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);

    PrintService defaultPrintService = null;
    for (PrintService printService : printServices) {
      if (printService.getName().equals(PRINTER_NAME)) {
        defaultPrintService = printService;
        break;
      }
    }

    if (defaultPrintService == null) {
      logger.error("Printer with specified name: {}, not found!\n", PRINTER_NAME);
      return;
    }

    // After download some browsers scan file for viruses and because of this file is locked
    boolean locked = true;
    while (locked) {
      try (FileInputStream is = new FileInputStream(path.toFile())) {
        locked = false;
      } catch (IOException ex) {
        locked = ex.getMessage().contains("used by another process");
      }

      if (locked) {
        try {
          Thread.sleep(1000L);
        } catch (InterruptedException ex) {
        }
      }
    }

    // Old browsers create temporary files while downloading and then delete it
    if (!path.toFile().exists()) {
      return;
    }

    int n = path.toFile().getName().toLowerCase().contains("thermal") ? 2 : 1;
    int cnt = 3;

    // Some browsers doesn't create temorary files, and we have wait for download to finish.
    // We are waiting only 3 seconds
    while (cnt > 0) {
      try (PDDocument pdfDoc = PDDocument.load(path.toFile())) {
        PrinterJob printerJob = PrinterJob.getPrinterJob();
        printerJob.setPageable(new PDFPageable(pdfDoc));
        printerJob.setPrintService(defaultPrintService);

        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();

        for (int i = 0; i < n; ++i) {
          printerJob.print(attributes);
        }

        logger.info("Printing file: {}!\n", path);

        cnt = 0;
      } catch (IOException ex) {
        try {
          Thread.sleep(1000L);
        } catch (InterruptedException ex1) {
        }

        --cnt;

        if (cnt == 0) {
          logger.error(String.format("File read failed: %s!\n", path.toString()), ex);
        }
      } catch (PrinterException ex) {
        logger.error(String.format("File print failed: %s!\n", path.toString()), ex);
      }
    }

    trash.add(path);
  }

  public static void stop(String[] args) {
    try {
      instance.watcher.close();
    } catch (IOException ex) {
      logger.error("Watcher close failed!\n", ex);
    }
  }

  public static void start(String[] args) {
    try {
      instance = new AutoPrinter();
      instance.processEvents();
    } catch (Exception ex) {
      logger.error("Watcher startup failed!\n", ex);
    }
  }

  public static void main(String[] args) {
    start(args);
  }
}
