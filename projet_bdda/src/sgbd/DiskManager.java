
package sgbd;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Scanner;

public class DiskManager implements IDiskManager {

    private String dmDir;
    private int pageSize;
    private RandomAccessFile file;
    private ArrayList<Integer> freePages;

    private boolean initAttempted = false;
    private boolean initialized = false;

    public DiskManager() {
        freePages = new ArrayList<>();
    }

    @Override
    public void Init(String dmDir, int pageSize) throws DiskManagerException {

        if (initAttempted) {
            throw new DiskManagerException("Init a déjà été appelé");
        }

        initAttempted = true;

        if (dmDir == null || dmDir.isBlank()) {
            throw new DiskManagerException("Le dossier est invalide");
        }

        if (pageSize <= 0) {
            throw new DiskManagerException("La taille d'une page doit être positive");
        }

        this.dmDir = dmDir;
        this.pageSize = pageSize;
        this.freePages = new ArrayList<>();

        File directory = new File(dmDir);

        // Création du dossier s'il n'existe pas
        if (!directory.exists() && !directory.mkdirs()) {
            throw new DiskManagerException("Impossible de créer le dossier");
        }

        if (!directory.isDirectory()) {
            throw new DiskManagerException("Le chemin indiqué n'est pas un dossier");
        }

        File pagesFile = new File(directory, "pages.dat");
        File configFile = new File(directory, "config.txt");

        try {
            // Création du fichier s'il n'existe pas
            if (!pagesFile.exists() && !pagesFile.createNewFile()) {
                throw new IOException("Impossible de créer pages.dat");
            }

            // Ouverture du fichier
            file = new RandomAccessFile(pagesFile, "rw");

            // Lecture des anciennes informations
            if (configFile.exists()) {
                try (Scanner scanner = new Scanner(configFile)) {

                    boolean pageSizeFound = false;

                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();

                        // Récupération de la taille des pages
                        if (line.startsWith("pageSize=")) {
                            int savedPageSize;

                            try {
                                savedPageSize = Integer.parseInt(
                                    line.substring("pageSize=".length())
                                );
                            } catch (NumberFormatException e) {
                                throw new DiskManagerException(
                                    "Taille de page sauvegardée invalide", e
                                );
                            }

                            if (savedPageSize != pageSize) {
                                throw new DiskManagerException(
                                    "La taille des pages est différente de celle sauvegardée"
                                );
                            }

                            pageSizeFound = true;
                        }

                        // Récupération des pages libres
                        if (line.startsWith("freePages=")) {
                            String pages = line.substring("freePages=".length());

                            if (!pages.isEmpty()) {
                                String[] numbers = pages.split(",");

                                for (String number : numbers) {
                                    try {
                                        int pageNumber = Integer.parseInt(number);

                                        if (pageNumber < 0
                                                || freePages.contains(pageNumber)) {
                                            throw new DiskManagerException(
                                                "Liste des pages libres invalide"
                                            );
                                        }

                                        freePages.add(pageNumber);

                                    } catch (NumberFormatException e) {
                                        throw new DiskManagerException(
                                            "Numéro de page invalide dans config.txt", e
                                        );
                                    }
                                }
                            }
                        }
                    }

                    if (!pageSizeFound) {
                        throw new DiskManagerException(
                            "La taille des pages est absente de config.txt"
                        );
                    }
                }
            }

            // Vérification que le fichier contient des pages complètes
            if (file.length() % pageSize != 0) {
                throw new DiskManagerException(
                    "Le fichier pages.dat est invalide"
                );
            }

            // Vérification des numéros de pages libres
            int totalPages = (int) (file.length() / pageSize);

            for (int pageNumber : freePages) {
                if (pageNumber >= totalPages) {
                    throw new DiskManagerException(
                        "Un numéro de page libre est invalide"
                    );
                }
            }

            initialized = true;

        } catch (IOException e) {
            closeFile();
            throw new DiskManagerException(
                "Erreur lors de l'initialisation du DiskManager", e
            );

        } catch (DiskManagerException e) {
            closeFile();
            throw e;
        }
    }

    @Override
    public void Save() throws DiskManagerException {
        checkInitialized();

        File configFile = new File(dmDir, "config.txt");

        try (PrintWriter writer = new PrintWriter(configFile)) {

            // Sauvegarde de la taille d'une page
            writer.println("pageSize=" + pageSize);

            // Sauvegarde des pages libres
            writer.print("freePages=");

            for (int i = 0; i < freePages.size(); i++) {
                writer.print(freePages.get(i));

                if (i < freePages.size() - 1) {
                    writer.print(",");
                }
            }

            writer.println();

            // PrintWriter ne lance pas forcément IOException en cas d'écriture ratée
            if (writer.checkError()) {
                throw new DiskManagerException(
                    "Erreur lors de la sauvegarde de config.txt"
                );
            }

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur lors de la sauvegarde", e
            );
        }
    }

    @Override
    public IPageId AllocPage() throws DiskManagerException {
        checkInitialized();

        // Cas 1 : une page libre existe
        if (!freePages.isEmpty()) {
            int pageNumber = freePages.remove(0);
            return new PageId(pageNumber);
        }

        // Cas 2 : aucune page libre, on crée une nouvelle page
        try {
            int pageNumber = (int) (file.length() / pageSize);

            file.seek(file.length());

            // Création d'une page vide
            byte[] emptyPage = new byte[pageSize];
            file.write(emptyPage);

            return new PageId(pageNumber);

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur lors de l'allocation d'une page", e
            );
        }
    }

    @Override
    public void ReadPage(IPageId ipid, ByteBuffer buffer)
            throws DiskManagerException {
        checkPage(ipid, buffer);

        PageId pageId = (PageId) ipid;

        try {
            // Position de la page dans le fichier
            long position = (long) pageId.getPageNumber() * pageSize;

            file.seek(position);

            // Lecture de la page dans un tableau temporaire
            byte[] data = new byte[pageSize];
            file.readFully(data);

            // Préparation du buffer avant la copie
            buffer.clear();
            buffer.put(data);

            // Préparation du buffer pour la lecture
            buffer.flip();

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur lors de la lecture d'une page", e
            );
        }
    }

    @Override
    public void WritePage(IPageId ipid, ByteBuffer buffer)
            throws DiskManagerException {
        checkPage(ipid, buffer);

        if (buffer.remaining() < pageSize) {
            throw new DiskManagerException(
                "Le buffer ne contient pas assez de données pour une page"
            );
        }

        PageId pageId = (PageId) ipid;

        try {
            // Position de la page dans le fichier
            long position = (long) pageId.getPageNumber() * pageSize;

            file.seek(position);

            // Copie des données du buffer sans modifier sa position
            ByteBuffer copy = buffer.duplicate();
            byte[] data = new byte[pageSize];
            copy.get(data);

            // Écriture de la page
            file.write(data);

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur lors de l'écriture d'une page", e
            );
        }
    }

    @Override
    public void DeallocPage(IPageId ipid) throws DiskManagerException {
        checkInitialized();

        if (!(ipid instanceof PageId)) {
            throw new DiskManagerException("Identifiant de page invalide");
        }

        PageId pageId = (PageId) ipid;
        int pageNumber = pageId.getPageNumber();

        // Vérification que la page existe
        try {
            int totalPages = (int) (file.length() / pageSize);

            if (pageNumber < 0 || pageNumber >= totalPages) {
                throw new DiskManagerException("Cette page n'existe pas");
            }

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur lors de la vérification de la page", e
            );
        }

        // Vérification que la page n'est pas déjà libre
        if (freePages.contains(pageNumber)) {
            throw new DiskManagerException("Cette page est déjà libérée");
        }

        freePages.add(pageNumber);
    }

    @Override
    public int GetActivePageCount() {
        if (!initialized || file == null) {
            return 0;
        }

        try {
            int totalPages = (int) (file.length() / pageSize);
            return totalPages - freePages.size();

        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public int GetPageSize() {
        return pageSize;
    }

    // Vérifie que le DiskManager a été initialisé
    private void checkInitialized() throws DiskManagerException {
        if (!initialized || file == null) {
            throw new DiskManagerException(
                "Le DiskManager n'a pas été initialisé correctement"
            );
        }
    }

    // Vérifie l'identifiant de la page et le buffer
    private void checkPage(IPageId ipid, ByteBuffer buffer)
            throws DiskManagerException {
        checkInitialized();

        if (!(ipid instanceof PageId)) {
            throw new DiskManagerException("Identifiant de page invalide");
        }

        if (buffer == null || buffer.capacity() < pageSize) {
            throw new DiskManagerException("Buffer invalide");
        }

        PageId pageId = (PageId) ipid;
        int pageNumber = pageId.getPageNumber();

        if (pageNumber < 0 || freePages.contains(pageNumber)) {
            throw new DiskManagerException(
                "La page est invalide ou a été libérée"
            );
        }

        try {
            int totalPages = (int) (file.length() / pageSize);

            if (pageNumber >= totalPages) {
                throw new DiskManagerException("Cette page n'existe pas");
            }

        } catch (IOException e) {
            throw new DiskManagerException(
                "Erreur lors de la vérification de la page", e
            );
        }
    }

    // Ferme le fichier si l'initialisation échoue
    private void closeFile() {
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                // On ne fait rien ici car on gère déjà une erreur
            }

            file = null;
        }

        initialized = false;
    }
}
