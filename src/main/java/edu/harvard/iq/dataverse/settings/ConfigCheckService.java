package edu.harvard.iq.dataverse.settings;

import edu.harvard.iq.dataverse.util.FileUtil;

import javax.annotation.PostConstruct;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Startup
@Singleton
@DependsOn("StartupFlywayMigrator")
public class ConfigCheckService {
    
    private static final Logger logger = Logger.getLogger(ConfigCheckService.class.getCanonicalName());

    public static class ConfigurationError extends RuntimeException {
        public ConfigurationError(String message) {
            super(message);
        }
    }
    
    @PostConstruct
    public void startup() {
        if (!checkSystemDirectories()) {
            throw new ConfigurationError("Not all configuration checks passed successfully. See logs above.");
        }
    }
    
    /**
     * In this method, we check the existence and write-ability of all important directories we use during
     * normal operations. It does not include checks for the storage system. If directories are not available,
     * try to create them (and fail when not allowed to).
     *
     * @return True if all checks successful, false otherwise.
     */
    public boolean checkSystemDirectories() {
        Map<Path, String> paths = Map.of(
                Path.of(JvmSettings.UPLOADS_DIRECTORY.lookup()), "temporary JSF upload space (see " + JvmSettings.UPLOADS_DIRECTORY.getScopedKey() + ")",
                Path.of(FileUtil.getFilesTempDirectory()), "temporary processing space (see " + JvmSettings.FILES_DIRECTORY.getScopedKey() + ")",
                Path.of(JvmSettings.DOCROOT_DIRECTORY.lookup()), "docroot space (see " + JvmSettings.DOCROOT_DIRECTORY.getScopedKey() + ")");
        
        boolean success = true;
        for (Path path : paths.keySet()) {
            if (Files.notExists(path)) {
                try {
                    Files.createDirectories(path);
                } catch (IOException e) {
                    String details;
                    if (e instanceof FileSystemException) {
                        details = ": " + e.getClass();
                    } else {
                        details = "";
                    }
                    
                    logger.log(Level.SEVERE, () -> "Could not create directory " + path + " for " + paths.get(path) + details);
                    success = false;
                }
            } else if (!Files.isWritable(path)) {
                logger.log(Level.SEVERE, () -> "Directory " + path + " for " + paths.get(path) + " exists, but is not writeable");
                success = false;
            }
        }
        return success;
    }

}
