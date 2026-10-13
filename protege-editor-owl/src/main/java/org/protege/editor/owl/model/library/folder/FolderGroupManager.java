package org.protege.editor.owl.model.library.folder;

import com.google.common.base.Strings;
import org.protege.editor.core.log.LogBanner;
import org.protege.editor.owl.model.library.CatalogEntryManager;
import org.protege.editor.owl.model.library.LibraryUtilities;
import org.protege.editor.owl.model.library.OntologyCatalogManager;
import org.protege.editor.owl.model.library.folder.PrioritizedAlgorithm.Priority;
import org.protege.editor.owl.model.library.folder.PrioritizedAlgorithm.Suggestions;
import org.protege.editor.owl.ui.UIHelper;
import org.protege.editor.owl.ui.library.NewEntryPanel;
import org.protege.editor.owl.ui.library.plugins.FolderGroupPanel;
import org.protege.xmlcatalog.CatalogUtilities;
import org.protege.xmlcatalog.Prefer;
import org.protege.xmlcatalog.XMLCatalog;
import org.protege.xmlcatalog.XmlBaseContext;
import org.protege.xmlcatalog.entry.Entry;
import org.protege.xmlcatalog.entry.GroupEntry;
import org.protege.xmlcatalog.entry.UriEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;


public class FolderGroupManager extends CatalogEntryManager {

    public static final int FOLDER_BY_URI_VERSION = 1;

    /*
     * Raising this number makes every existing catalog rebuild itself once (see
     * ensureLatestVersion). Version 3: older catalogs only ever indexed files by
     * xml:base, and updates never re-read unchanged files, so without a rebuild
     * they would never learn the IRIs the new scan finds.
     */
    public static final int CURRENT_VERSION = 3;

    public static final String ID_PREFIX = "Folder Repository";

    public static final String DIR_PROP = "directory";

    public static final String RECURSIVE_PROP = "recursive";

    public static final String FILE_KEY = "FILE";

    /*
     * Each generated entry records whether its IRI was read from the file or built
     * by convention. Later updates keep entries for unchanged files instead of
     * rescanning them, so this is how a read IRI can still win over a built one.
     * No property means "read from the file"; entries older than version 3 all
     * count as such.
     */
    static final String PRIORITY_PROP = "Priority";

    static final String SECONDARY_PRIORITY_VALUE = "secondary";

    private static final int NON_ONTOLOGY_DOCUMENT_TERMINATION_LIMIT = 1000;

    private final Logger logger = LoggerFactory.getLogger(FolderGroupManager.class);

    private Set<Algorithm> algorithms;

    private boolean autoUpdate = true;

    private boolean warnedUserOfBadRepositoryDeclaration = false;

    /*
     * parameters used by the non-reentrant update routine
     */
    private GroupEntry ge;

    private File folder;

    private boolean recursive = true;

    private long timeOfCurrentUpdate;

    private boolean modified = false;

    private Map<File, Map<URI, Priority>> retainedFileToWebLocationMap = new TreeMap<>();

    /** IRI claimed -> (file claiming it -> whether the claim is declared or derived). */
    private Map<URI, Map<URI, Priority>> webLocationToFileLocationMap = new TreeMap<>();


    public FolderGroupManager() {
        algorithms = new HashSet<>();
        algorithms.add(new OntologyIriExtractionAlgorithm());
    }

    public static GroupEntry createGroupEntry(URI folder,
                                              boolean recursive,
                                              boolean autoUpdate,
                                              XmlBaseContext context) throws IOException {
        return new GroupEntry(getIdString(ID_PREFIX, folder, recursive, autoUpdate), context, Prefer.PUBLIC,
                              folder.toString().length() > 0 ? folder : null);
    }

    protected static String getIdString(String idPrefix,
                                        URI folderUri,
                                        boolean recursive,
                                        boolean autoUpdate) {
        return getIdString(idPrefix, folderUri.toString(), recursive, autoUpdate);
    }

    protected static String getIdString(String idPrefix,
                                        String folderUri,
                                        boolean recursive,
                                        boolean autoUpdate) {
        StringBuffer sb = new StringBuffer(idPrefix);
        LibraryUtilities.addPropertyValue(sb, DIR_PROP, folderUri);
        LibraryUtilities.addPropertyValue(sb, RECURSIVE_PROP, recursive);
        LibraryUtilities.addPropertyValue(sb, LibraryUtilities.AUTO_UPDATE_PROP, autoUpdate ? "true" : "false");
        LibraryUtilities.addPropertyValue(sb, LibraryUtilities.VERSION_PROPERTY, CURRENT_VERSION);
        return sb.toString();
    }

    protected static boolean isValidOWLFile(File physicalLocation) {
        if(physicalLocation.getName().startsWith(".")) {
            return false;
        }
        String path = physicalLocation.getPath();
        for(String extension : UIHelper.OWL_EXTENSIONS) {
            if(path.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static URI appendScheme(URI u,
                                    String scheme) {
        String uString = u.toString();
        return URI.create(scheme + uString);
    }

    private static URI removeIgnoredSchemes(URI u) {
        String uString = u.toString();
        for(String iScheme : CatalogEntryManager.IGNORED_SCHEMES) {
            if(uString.startsWith(iScheme)) {
                return URI.create(uString.substring(iScheme.length()));
            }
        }
        return u;
    }

    private static boolean isIgnored(URI u) {
        String uString = u.toString();
        for(String iScheme : CatalogEntryManager.IGNORED_SCHEMES) {
            if(uString.startsWith(iScheme)) {
                return true;
            }
        }
        return false;
    }

    public void setAlgorithms(Algorithm... algorithms) {
        this.algorithms.clear();
        Collections.addAll(this.algorithms, algorithms);
    }

    public boolean isSuitable(Entry entry) {
        if(!(entry instanceof GroupEntry)) {
            return false;
        }
        GroupEntry ge = (GroupEntry) entry;
        File dir = getDirectory(ge);

        boolean enabled = LibraryUtilities.getBooleanProperty(ge, LibraryUtilities.AUTO_UPDATE_PROP, false);
        boolean hasRightType = ge.getId() != null && ge.getId().startsWith(getIdPrefix()) && enabled && dir != null;

        if(hasRightType && (!dir.exists() || !dir.isDirectory())) {
            logger.warn("Folder repository probably came from another system");
            logger.warn("Could not be updated because directory " + dir + " does not exist");
            if(!warnedUserOfBadRepositoryDeclaration) {
                logger.error("Bad ontology library declaration - check logs. Warnings now disabled for this session.");
                warnedUserOfBadRepositoryDeclaration = true;
            }
            return false;
        }
        return hasRightType;
    }

    private static File getDirectory(GroupEntry ge) {
        String dirName = LibraryUtilities.getStringProperty(ge, DIR_PROP);
        if(dirName == null) {
            return null;
        }
        final File folder;
        if(LibraryUtilities.getVersion(ge) < FOLDER_BY_URI_VERSION) {
            folder = new File(dirName);
        }
        else {
            URI dirURI = CatalogUtilities.resolveXmlBase(ge).resolve(dirName);
            folder = new File(dirURI);
        }
        return folder;
    }

    protected String getIdPrefix() {
        return ID_PREFIX;
    }

    public boolean update(Entry entry) {
        this.ge = (GroupEntry) entry;
        reset();
        ensureLatestVersion();
        try {
            logger.info(LogBanner.start("Starting Catalog Update"));
            logger.info("Update of group entry {} started at {}.", ge.getId(), new Date(timeOfCurrentUpdate));

            retainEntries();
            if(folder != null) {
                HashSet<File> nonOwlFiles = new HashSet<>();
                examineDirectoryContents(folder, new HashSet<>(), nonOwlFiles, 0);
                if(nonOwlFiles.size() > NON_ONTOLOGY_DOCUMENT_TERMINATION_LIMIT) {
                    logger.warn("Search for ontology documents in {} and sub-folders has been terminated as over {} non-ontology documents have been found.",
                                folder.getAbsolutePath(),
                                NON_ONTOLOGY_DOCUMENT_TERMINATION_LIMIT);
                }
            }
            if(modified) {
                clearEntries();
                writeEntries();
            }
            logger.info("Catalog Update Complete");
            logger.info(LogBanner.end());
            return modified;
        } finally {
            this.ge = null;
            reset();
        }
    }

    public boolean initializeCatalog(File folder,
                                     XMLCatalog catalog) throws IOException {
        URI relativeFolderUri = CatalogUtilities.relativize(folder.toURI(), catalog);
        ge = FolderGroupManager.createGroupEntry(relativeFolderUri, true, autoUpdate, catalog);
        catalog.addEntry(ge);
        update(ge);
        return true;
    }

    public NewEntryPanel newEntryPanel(XMLCatalog catalog) {
        return new FolderGroupPanel(catalog);
    }

    public String getDescription() {
        return "Folder";
    }

    public String getDescription(Entry ge) {
        StringBuilder sb = new StringBuilder("<html><body><b>Folder ");
        sb.append(getDirectory((GroupEntry) ge));
        sb.append("</b>");
        if(LibraryUtilities.getBooleanProperty(ge, RECURSIVE_PROP, true)) {
            sb.append(" <font color=\"gray\">(includes sub-folders)</font>");
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private void reset() {
        modified = false;
        timeOfCurrentUpdate = System.currentTimeMillis();
        retainedFileToWebLocationMap.clear();
        webLocationToFileLocationMap.clear();
        if(ge != null) {
            folder = getDirectory(ge);
            recursive = LibraryUtilities.getBooleanProperty(ge, RECURSIVE_PROP, true);
        }
        else {
            folder = null;
        }
    }

    /*
     * A version bump means one regeneration of the generated group, nothing more.
     * The directory property is kept as written: an empty value means "the folder
     * holding this catalog", which is what lets a folder be moved or shared. The
     * bump is itself a change that must reach disk, even when the folder turns out
     * to hold no ontology documents.
     */
    private void ensureLatestVersion() {
        int version = LibraryUtilities.getVersion(ge);
        if(version < CURRENT_VERSION) {
            boolean autoUpdate = LibraryUtilities.getBooleanProperty(ge, LibraryUtilities.AUTO_UPDATE_PROP, this.autoUpdate);
            ge.setId(getIdString(getIdPrefix(), migratedDirectoryProperty(version), recursive, autoUpdate));
            clearEntries();
            modified = true;
        }
    }

    private String migratedDirectoryProperty(int version) {
        String dirName = LibraryUtilities.getStringProperty(ge, DIR_PROP);
        if(version >= FOLDER_BY_URI_VERSION || folder == null) {
            return dirName;   // already a URI relative to the catalog
        }
        // Before FOLDER_BY_URI_VERSION the property held a plain file system path;
        // store it the way new catalogs do.
        return CatalogUtilities.relativize(folder.toURI(), ge).toString();
    }

    private void retainEntries() {
        for(Entry e : new ArrayList<>(ge.getEntries())) {
            if(e instanceof UriEntry) {
                UriEntry ue = (UriEntry) e;
                try {
                    long lastUpdated = -1;
                    String updatedString = LibraryUtilities.getStringProperty(ue, OntologyCatalogManager.TIMESTAMP);
                    try {
                        if(updatedString != null) {
                            lastUpdated = Long.parseLong(updatedString);
                        }
                    } catch(NumberFormatException nfe) {
                        logger.info("Could not parse timestamps in catalog file " + nfe);
                    }
                    File f = new File(ue.getAbsoluteURI());
                    if(!f.exists() || f.lastModified() >= lastUpdated) {
                        modified = true;
                        if(logger.isDebugEnabled()) {
                            logger.debug("Map for file " + f + " is stale and has been removed");
                        }
                    }
                    else {
                        if(logger.isDebugEnabled()) {
                            logger.debug("Map for file " + f + " is still good and will be kept");
                        }
                        recordRetainedEntry(URI.create(ue.getName()), f.getCanonicalFile(), priorityOf(ue));
                    }
                } catch(Throwable t) {
                    logger.error("Exception caught updating catalog entry.", t);
                }
            }
        }
    }

    private void examineDirectoryContents(@Nonnull File directory,
                                          Set<URI> webLocationsFoundInParentDirectory,
                                          Set<File> nonOwlFiles,
                                          int depth) {
        if(nonOwlFiles.size() > NON_ONTOLOGY_DOCUMENT_TERMINATION_LIMIT) {
            return;
        }
        logger.info("{} Examining: {}", pad(depth), directory.getAbsolutePath());
        Set<URI> newWebLocations = new HashSet<>();
        if(algorithms == null || algorithms.isEmpty()) {
            return;
        }
        Set<File> subFolders = new HashSet<>();
        File[] directoryEntries = directory.listFiles();
        if(directoryEntries == null) { // I think that this means that there was an I/O error
            return;
        }
        for(File physicalLocation : directoryEntries) {
            if(!physicalLocation.isHidden() && physicalLocation.exists()) {
                if(physicalLocation.isDirectory()) {
                    if(recursive) {
                        subFolders.add(physicalLocation);
                    }
                }
                else if(physicalLocation.isFile()) {
                    if(isValidOWLFile(physicalLocation)) {
                        examineSingleFile(physicalLocation, webLocationsFoundInParentDirectory, newWebLocations);
                    }
                    else {
                        nonOwlFiles.add(physicalLocation);
                    }
                }
            }
        }
        webLocationsFoundInParentDirectory.addAll(newWebLocations);
        for(File physicalLocation : subFolders) {
            examineDirectoryContents(physicalLocation, webLocationsFoundInParentDirectory, nonOwlFiles, depth + 1);
        }
    }

    private static String pad(int depth) {
        return Strings.repeat(" ", depth * 4);
    }

    private void examineSingleFile(File physicalLocation,
                                   Set<URI> webLocationsFoundInParentDirectory,
                                   Set<URI> newWebLocations) {
        if(logger.isDebugEnabled()) {
            logger.debug("Applying algorithms to " + physicalLocation);
        }
        URI shortLocation = folder.toURI().relativize(physicalLocation.toURI());
        Map<URI, Priority> retainedSuggestions = null;
        try {
            retainedSuggestions = retainedFileToWebLocationMap.get(physicalLocation.getCanonicalFile());
        } catch(IOException e) {
            logger.warn("IO Exception caught processing file " + physicalLocation + " for repository library update", e);
        }
        if(retainedSuggestions != null) {
            if(logger.isDebugEnabled()) {
                logger.debug("Adding mappings retained from previous version of the catalog");
            }
            for(Map.Entry<URI, Priority> retained : retainedSuggestions.entrySet()) {
                recordEntries(Collections.singleton(retained.getKey()), shortLocation,
                              webLocationsFoundInParentDirectory, newWebLocations, retained.getValue());
            }
        }
        else {
            if(logger.isDebugEnabled()) {
                logger.debug("Adding new mappings not found in the previous version of the catalog");
            }
            for(Algorithm algorithm : algorithms) {
                Suggestions suggestions = prioritizedSuggestions(algorithm, physicalLocation);
                modified = modified || !suggestions.isEmpty();
                recordEntries(suggestions.primary, shortLocation, webLocationsFoundInParentDirectory, newWebLocations, Priority.PRIMARY);
                recordEntries(suggestions.secondary, shortLocation, webLocationsFoundInParentDirectory, newWebLocations, Priority.SECONDARY);
            }
        }
    }

    private static Suggestions prioritizedSuggestions(Algorithm algorithm, File physicalLocation) {
        if(algorithm instanceof PrioritizedAlgorithm) {
            return ((PrioritizedAlgorithm) algorithm).getPrioritizedSuggestions(physicalLocation);
        }
        // Plain algorithms make no distinction: everything they return counts as declared.
        return new Suggestions(algorithm.getSuggestions(physicalLocation), Collections.emptySet());
    }

    private static Priority priorityOf(UriEntry entry) {
        String value = LibraryUtilities.getStringProperty(entry, PRIORITY_PROP);
        return SECONDARY_PRIORITY_VALUE.equalsIgnoreCase(value) ? Priority.SECONDARY : Priority.PRIMARY;
    }

    private void recordEntries(Collection<URI> webLocations,
                               URI physicalLocation,
                               Set<URI> webLocationsFoundInParentDirectory,
                               Set<URI> newWebLocations,
                               Priority priority) {
        for(URI webLocation : webLocations) {
            if(!webLocationsFoundInParentDirectory.contains(webLocation)) {
                newWebLocations.add(webLocation);
                recordEntry(webLocation, physicalLocation, priority);
            }
            else {
                recordEntry(appendScheme(webLocation, CatalogEntryManager.SHADOWED_SCHEME), physicalLocation, priority);
            }
        }
    }

    private void recordEntry(URI webLocation,
                             URI physicalLocation,
                             Priority priority) {
        if(logger.isDebugEnabled()) {
            logger.debug("Found " + priority + " mapping from import location " + webLocation + " to physical file " + physicalLocation);
        }
        Map<URI, Priority> claims = webLocationToFileLocationMap.computeIfAbsent(webLocation, k -> new LinkedHashMap<>());
        Priority existing = claims.get(physicalLocation);
        if(existing != Priority.PRIMARY) {   // a declared claim by the same file is never downgraded
            claims.put(physicalLocation, priority);
        }
    }

    private void recordRetainedEntry(URI webLocation,
                                     File f,
                                     Priority priority) {
        retainedFileToWebLocationMap.computeIfAbsent(f, k -> new LinkedHashMap<>())
                                    .put(removeIgnoredSchemes(webLocation), priority);
    }

    private void clearEntries() {
        if(logger.isDebugEnabled()) {
            logger.debug("Catalog must be modified - clearing out existing data");
        }
        for(Entry e : ge.getEntries()) {
            ge.removeEntry(e);
        }
    }

    /*
     * One IRI, several files. A file that states the IRI wins over files that only
     * imply it. Several files stating it are a duplicate, as before. Implied claims
     * compete among themselves only when no file states the IRI.
     */
    private void writeEntries() {
        if(logger.isDebugEnabled()) {
            logger.debug("Catalog must be modified - writing new data");
        }
        for(Map.Entry<URI, Map<URI, Priority>> claim : webLocationToFileLocationMap.entrySet()) {
            URI webLocation = claim.getKey();
            List<URI> declared = new ArrayList<>();
            List<URI> derived = new ArrayList<>();
            for(Map.Entry<URI, Priority> byFile : claim.getValue().entrySet()) {
                (byFile.getValue() == Priority.PRIMARY ? declared : derived).add(byFile.getKey());
            }
            Priority priority = declared.isEmpty() ? Priority.SECONDARY : Priority.PRIMARY;
            List<URI> physicalLocations = declared.isEmpty() ? derived : declared;
            if(physicalLocations.size() > 1 && !isIgnored(webLocation)) {
                writeEntries(appendScheme(webLocation, CatalogEntryManager.DUPLICATE_SCHEME), physicalLocations, priority);
            }
            else {
                writeEntries(webLocation, physicalLocations, priority);
            }
        }
    }

    private void writeEntries(URI webLocation,
                              Collection<URI> physicalLocations,
                              Priority priority) {
        for(URI physicalLocation : physicalLocations) {
            StringBuilder entryId = new StringBuilder("Automatically generated entry, ")
                    .append(OntologyCatalogManager.TIMESTAMP).append("=").append(timeOfCurrentUpdate);
            if(priority == Priority.SECONDARY) {
                entryId.append(", ").append(PRIORITY_PROP).append("=").append(SECONDARY_PRIORITY_VALUE);
            }
            UriEntry u = new UriEntry(entryId.toString(), ge, webLocation.toString(), physicalLocation, null);
            ge.addEntry(u);
            modified = true;
        }
    }

}
