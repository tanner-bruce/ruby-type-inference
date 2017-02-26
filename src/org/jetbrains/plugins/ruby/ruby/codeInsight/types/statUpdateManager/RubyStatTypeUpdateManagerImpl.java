package org.jetbrains.plugins.ruby.ruby.codeInsight.types.statUpdateManager;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.gem.GemInfo;
import org.jetbrains.plugins.ruby.gem.GemManager;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.RSignatureManager;
import org.jetbrains.plugins.ruby.ruby.codeInsight.types.signatureManager.SqliteRSignatureManager;
import ruby.codeInsight.types.signature.RSignature;
import ruby.codeInsight.types.storage.server.RSignatureStorageServer;
import ruby.codeInsight.types.storage.server.RSignatureStorageServerImpl;
import ruby.codeInsight.types.storage.server.StatFileInfo;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RubyStatTypeUpdateManagerImpl extends RubyStatTypeUpdateManager {
    @NotNull
    private static final Logger LOG = Logger.getInstance(RubyStatTypeUpdateManager.class.getName());

    @Nullable
    private static RubyStatTypeUpdateManager ourInstance;

    @Nullable
    public static RubyStatTypeUpdateManager getInstance() {
        if (ourInstance == null) {
            ourInstance = new RubyStatTypeUpdateManagerImpl();
        }

        return ourInstance;
    }

    @Override
    public void updateLocalStat(@NotNull final Project project, @NotNull final Module module) {
        final RSignatureStorageServer server;
        try {
            server = new RSignatureStorageServerImpl();
        } catch (SQLException | ClassNotFoundException e) {
            LOG.error(e);
            return;
        }

        final RSignatureManager signatureManager = SqliteRSignatureManager.getInstance();
        if (signatureManager == null) {
            return;
        }

        // 1. Get a list of stat files from server
        final List<StatFileInfo> statFiles = server.getStatFileInfos(true);

        // 2. Get a list of current gems
        final Set<GemInfo> gems = GemManager.getAllGems(module);

        // 3. Select appropriate stat files for my gems
        final List<StatFileInfo> neededStatFiles = gems.stream()
                .map(gem -> getClosestStatFile(gem, statFiles))
                .collect(Collectors.toList());

        // 4. Filter out unwanted files
        neededStatFiles.removeIf(statFile -> {
            final long localLastModified = signatureManager.getStatFileLastModified(
                    statFile.getGemName(), statFile.getGemVersion());
            return statFile.getLastModified() <= localLastModified;
        });

        // 5. Download stat files and add new stat to the DB,
        //    then update last modified time for stat files in the DB table
        for (final StatFileInfo statFile : neededStatFiles) {
            try {
                final List<RSignature> signatures = server.getSignaturesFromStatFile(statFile.getFullGemName(), true);
                signatures.forEach(signatureManager::recordSignature);
                signatureManager.setStatFileLastModified(
                        statFile.getGemName(), statFile.getGemVersion(), statFile.getLastModified());
            } catch (IOException e) {
                LOG.error(e);
            }
        }
    }

    @Override
    public void uploadCollectedStat() {
        final RSignatureStorageServer server;
        try {
            server = new RSignatureStorageServerImpl();
        } catch (SQLException | ClassNotFoundException e) {
            LOG.error(e);
            return;
        }

        final RSignatureManager signatureManager = SqliteRSignatureManager.getInstance();
        if (signatureManager == null) {
            return;
        }

        final List<RSignature> signatures = signatureManager.getLocalSignatures();
        final String statFileName = UUID.randomUUID().toString() + ".json";
        server.insertSignaturesToStatFile(signatures, statFileName, false);
    }

    @Nullable
    private StatFileInfo getClosestStatFile(@NotNull final GemInfo gemInfo,
                                            @NotNull List<StatFileInfo> statFileInfos) {
        statFileInfos = statFileInfos.stream()
                .filter(statFileInfo -> statFileInfo.getGemName().equals(gemInfo.getName()))
                .collect(Collectors.toList());
        final List<String> gemVersions = statFileInfos.stream()
                .map(StatFileInfo::getGemVersion)
                .collect(Collectors.toList());
        if (gemVersions.isEmpty()) {
            return null;
        }

        final String closestGemVersion = SqliteRSignatureManager.getClosestGemVersion(gemInfo.getVersion(), gemVersions);
        return statFileInfos.stream()
                .filter(statFileInfo -> statFileInfo.getGemVersion().equals(closestGemVersion))
                .findAny()
                .get();
    }

    private RubyStatTypeUpdateManagerImpl() {
    }
}
