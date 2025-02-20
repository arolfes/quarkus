package io.quarkus.devtools.commands.handlers;

import static io.quarkus.devtools.commands.AddExtensions.EXTENSION_MANAGER;
import static io.quarkus.devtools.messagewriter.MessageIcons.ERROR_ICON;
import static io.quarkus.devtools.messagewriter.MessageIcons.NOK_ICON;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.quarkus.devtools.commands.AddExtensions;
import io.quarkus.devtools.commands.data.QuarkusCommandException;
import io.quarkus.devtools.commands.data.QuarkusCommandInvocation;
import io.quarkus.devtools.commands.data.QuarkusCommandOutcome;
import io.quarkus.devtools.messagewriter.MessageIcons;
import io.quarkus.devtools.project.extensions.ExtensionInstallPlan;
import io.quarkus.devtools.project.extensions.ExtensionManager;
import io.quarkus.devtools.project.extensions.ExtensionManager.InstallResult;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.platform.catalog.predicate.ExtensionPredicate;
import io.quarkus.registry.catalog.Extension;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.ExtensionOrigin;

/**
 * This class is thread-safe. It extracts extensions to be added to the project from an instance of
 * {@link QuarkusCommandInvocation}.
 */
public class AddExtensionsCommandHandler implements QuarkusCommandHandler {

    @Override
    public QuarkusCommandOutcome execute(QuarkusCommandInvocation invocation) throws QuarkusCommandException {
        final Set<String> extensionsQuery = invocation.getValue(AddExtensions.EXTENSIONS, Collections.emptySet());
        if (extensionsQuery.isEmpty()) {
            return QuarkusCommandOutcome.success().setValue(AddExtensions.OUTCOME_UPDATED, false);
        }

        final ExtensionManager extensionManager = invocation.getValue(EXTENSION_MANAGER,
                invocation.getQuarkusProject().getExtensionManager());
        try {
            ExtensionInstallPlan extensionInstallPlan = planInstallation(invocation, extensionsQuery);
            if (extensionInstallPlan.isInstallable()) {
                final InstallResult result = extensionManager.install(extensionInstallPlan);
                result.getInstalledPlatforms()
                        .forEach(a -> invocation.log()
                                .info(MessageIcons.OK_ICON + " Platform " + a.getGroupId() + ":" + a.getArtifactId()
                                        + " has been installed"));
                result.getInstalledManagedExtensions()
                        .forEach(a -> invocation.log()
                                .info(MessageIcons.OK_ICON + " Extension " + a.getGroupId() + ":" + a.getArtifactId()
                                        + " has been installed"));
                result.getInstalledIndependentExtensions()
                        .forEach(a -> invocation.log()
                                .info(MessageIcons.OK_ICON + " Extension " + a.getGroupId() + ":" + a.getArtifactId() + ":"
                                        + a.getVersion()
                                        + " has been installed"));
                result.getAlreadyInstalled()
                        .forEach(a -> invocation.log()
                                .info(MessageIcons.NOOP_ICON + " Extension " + a.getGroupId() + ":" + a.getArtifactId()
                                        + " was already installed"));
                return new QuarkusCommandOutcome(true).setValue(AddExtensions.OUTCOME_UPDATED, result.isSourceUpdated());
            } else if (!extensionInstallPlan.getUnmatchedKeywords().isEmpty()) {
                invocation.log()
                        .info(ERROR_ICON + " Nothing installed because keyword(s) '"
                                + String.join("', '", extensionInstallPlan.getUnmatchedKeywords())
                                + "' were not matched in the catalog.");
            } else {
                invocation.log()
                        .info(NOK_ICON + " The provided keyword(s) did not match any extension from the catalog.");
            }
        } catch (MultipleExtensionsFoundException m) {
            StringBuilder sb = new StringBuilder();
            sb.append(ERROR_ICON + " Multiple extensions matching '").append(m.getKeyword()).append("'");
            m.getExtensions()
                    .forEach(extension -> sb.append(System.lineSeparator()).append("     - ")
                            .append(extension.managementKey()));
            sb.append(System.lineSeparator())
                    .append("     Be more specific e.g using the exact name or the full GAV.");
            invocation.log().info(sb.toString());
        } catch (IOException e) {
            throw new QuarkusCommandException("Failed to add extensions", e);
        }

        return new QuarkusCommandOutcome(false).setValue(AddExtensions.OUTCOME_UPDATED, false);
    }

    public ExtensionInstallPlan planInstallation(QuarkusCommandInvocation invocation, Collection<String> keywords)
            throws IOException {
        if (keywords.isEmpty()) {
            return ExtensionInstallPlan.EMPTY;
        }
        final ExtensionCatalog catalog = invocation.getExtensionsCatalog();
        final String quarkusCore = catalog.getQuarkusCoreVersion();
        final Collection<ArtifactCoords> importedPlatforms = invocation.getQuarkusProject().getExtensionManager()
                .getInstalledPlatforms();
        // TODO
        Set<ArtifactCoords> preferredBoms = Collections.emptySet();

        ExtensionInstallPlan.Builder builder = ExtensionInstallPlan.builder();
        for (String keyword : keywords) {
            int countColons = StringUtils.countMatches(keyword, ":");
            if (countColons > 1) {
                // it's a gav
                builder.addIndependentExtension(ArtifactCoords.fromString(keyword));
                continue;
            }
            List<Extension> listed = List.of();
            // Check if it's just groupId:artifactId
            if (countColons == 1) {
                ArtifactKey artifactKey = ArtifactKey.fromString(keyword);
                for (Extension e : invocation.getExtensionsCatalog().getExtensions()) {
                    if (e.getArtifact().getKey().equals(artifactKey)) {
                        listed = List.of(e);
                        break;
                    }
                }

                //builder.addManagedExtension(new ArtifactCoords(artifactKey, null));
            } else {
                listed = listInternalExtensions(quarkusCore, keyword, catalog.getExtensions());
            }
            if (listed.isEmpty()) {
                // No extension found for this keyword.
                builder.addUnmatchedKeyword(keyword);
            }
            // If it's a pattern allow multiple results
            // See https://github.com/quarkusio/quarkus/issues/11086#issuecomment-666360783
            else if (listed.size() > 1 && !ExtensionPredicate.isPattern(keyword)) {
                throw new MultipleExtensionsFoundException(keyword, listed);
            }
            for (Extension e : listed) {
                final ArtifactCoords extensionCoords = e.getArtifact();
                boolean managed = false;
                ExtensionOrigin firstPlatform = null;
                ExtensionOrigin preferredOrigin = null;
                for (ExtensionOrigin origin : e.getOrigins()) {
                    if (!origin.isPlatform()) {
                        continue;
                    }
                    if (importedPlatforms.contains(origin.getBom())) {
                        managed = true;
                        builder.addManagedExtension(extensionCoords);
                        break;
                    }
                    if (preferredOrigin == null) {
                        if (preferredBoms.contains(origin.getBom())) {
                            preferredOrigin = origin;
                        } else if (firstPlatform == null) {
                            firstPlatform = origin;
                        }
                    }
                }

                if (preferredOrigin == null) {
                    preferredOrigin = firstPlatform;
                }

                if (!managed && preferredOrigin != null) {
                    builder.addManagedExtension(extensionCoords);
                    builder.addPlatform(preferredOrigin.getBom());
                    managed = true;
                }
                if (!managed) {
                    builder.addIndependentExtension(extensionCoords);
                }
            }
        }
        return builder.build();
    }

    private List<Extension> listInternalExtensions(String quarkusCore, String keyword, Collection<Extension> extensions) {
        List<Extension> result = new ArrayList<>();
        ExtensionPredicate predicate = null;
        if (keyword != null && !keyword.isEmpty()) {
            predicate = new ExtensionPredicate(keyword);
        }
        for (Extension extension : extensions) {
            // If no filter is defined, just return the tuple
            if (predicate == null) {
                result.add(extension);
            } else {
                // If there is an exact match, return only this
                if (predicate.isExactMatch(extension)) {
                    return Collections.singletonList(extension);
                } else if (predicate.test(extension)) {
                    result.add(extension);
                }
            }
        }
        return result;
    }

}
