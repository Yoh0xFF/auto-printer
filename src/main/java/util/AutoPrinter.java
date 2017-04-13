package util;

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
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;

public class AutoPrinter {

    private static final Logger LOGGER;
    private static final String PRINTER_NAME;
    private static final String WATCH_DIR;
    private static final String FILE_MIME_TYPE;
    private static final String FILE_NAME_PATTERN;

    static {
        LOGGER = LogManager.getLogger(AutoPrinter.class.getName());
        
        Properties props = new Properties();
        try (InputStream in = Files.exists(Paths.get("./config.properties")) 
                ? new FileInputStream("./config.properties")
                : AutoPrinter.class.getResourceAsStream("/config.properties")) {
            props.load(in);
        } catch (IOException | NullPointerException ex) {
            LOGGER.error("Application configuration not found!", ex);
            throw new AssertionError("Application configuration not found", ex);
        }

        PRINTER_NAME = props.getProperty("printerName");
        WATCH_DIR = props.getProperty("watchDir");
        FILE_MIME_TYPE = props.getProperty("fileMimeType");
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
                LOGGER.error("Watcher interrupted!", ex);
                break;
            } catch (ClosedWatchServiceException ex) {
                LOGGER.info("Watcher is closed!");
                break;
            }
            
            while (!trash.isEmpty()) {
                trash.get(0).toFile().delete();
                trash.remove(0);
            }

            for (WatchEvent<?> event : crntWatchKey.pollEvents()) {
                if (event.kind() == OVERFLOW) {
                    continue;
                }
                
                Path path = dir.resolve(((WatchEvent<Path>) event).context());
                
                try {
                    String mimeType = Files.probeContentType(path);

                    LOGGER.info("{}, {}, {}\n", event.kind().name(), path, StringUtils.isBlank(mimeType) ? "Unknown" : mimeType);
                    
                    if (FILE_MIME_TYPE.equals(mimeType) && fileNamePattern.matcher(path.toFile().getName()).matches()) {
                        print(path);
                        trash.add(path);
                    }
                } catch (IOException ex) {
                    LOGGER.error(String.format("Cannot read file: %s!", path.toString()), ex);
                }
            }

            boolean valid = crntWatchKey.reset();
            if (!valid) {
                LOGGER.error("WatchKey reset failed!");
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
            LOGGER.error("Printer with specified name: {}, not found!", PRINTER_NAME);
            return;
        }
        
        int n = path.toFile().getName().toLowerCase().contains("thermal") ? 2 : 1;
        
        try (PDDocument pdfDoc = PDDocument.load(path.toFile())) {
            PrinterJob printerJob = PrinterJob.getPrinterJob();
            printerJob.setPageable(new PDFPageable(pdfDoc));
            printerJob.setPrintService(defaultPrintService);
            
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            
            for (int i = 0; i < n; ++i) {
                printerJob.print(attributes);
            }
            LOGGER.info("Printing file: {}!", path);
        } catch (IOException ex) {
            LOGGER.error(String.format("File read failed: %s!", path.toString()), ex);
        } catch (PrinterException ex) {
            LOGGER.error(String.format("File print failed: %s!", path.toString()), ex);
        }
    }

    public static void stop(String[] args) {
        try {
            instance.watcher.close();
        } catch (IOException ex) {
            LOGGER.error("Watcher close failed!", ex);
        }
    }
    
    public static void start(String[] args) {
        try {
            instance = new AutoPrinter();
            instance.processEvents();
        } catch (Exception ex) {
            LOGGER.error("Watcher startup failed!", ex);
        }
    }
    
    public static void main(String[] args) {
        start(args);
    }
}